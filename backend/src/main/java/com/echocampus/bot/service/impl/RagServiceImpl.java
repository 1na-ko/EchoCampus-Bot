package com.echocampus.bot.service.impl;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * RAG检索增强生成服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final MilvusService milvusService;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocMapper docMapper;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.6}")
    private float similarityThreshold;

    @Value("${rag.max-context-length:4000}")
    private int maxContextLength;

    @Override
    public RagResponse answer(String question, List<Message> historyMessages, Long userId, Long conversationId) {
        long startTime = System.currentTimeMillis();
        
        log.info("RAG问答开始: question={}, userId={}, historyCount={}", question, userId, historyMessages.size());

        // 1. 检索相关知识片段
        List<KnowledgeChunk> relevantChunks = retrieve(question, defaultTopK);
        
        // 2. 构建知识库上下文
        String context = buildContext(relevantChunks);
        
        // 3. 生成回答（携带历史消息）
        String answer = llmService.ragAnswer(question, context, historyMessages);
        
        // 4. 构建来源信息（需要从 Milvus 重新获取分数）
        List<SourceInfo> sources = buildSourcesWithScores(relevantChunks, question);
        
        long responseTime = System.currentTimeMillis() - startTime;
        log.info("RAG问答完成: 耗时={}ms, 检索到{}个片段", responseTime, relevantChunks.size());

        return new RagResponse(answer, sources, responseTime);
    }

    @Override
    public List<KnowledgeChunk> retrieve(String question, int topK) {
        if (question == null || question.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 1. 将问题向量化
            float[] queryVector = embeddingService.embed(question);
            
            if (queryVector == null || allZeros(queryVector)) {
                log.warn("问题向量化失败: {}", question);
                return Collections.emptyList();
            }

            // 2. 在Milvus中搜索相似向量
            List<MilvusService.SearchResult> searchResults = 
                    milvusService.search(queryVector, topK, similarityThreshold);

            if (searchResults.isEmpty()) {
                log.info("未找到相关知识片段: question={}", question);
                return Collections.emptyList();
            }

            // 3. 根据chunkId获取完整的知识片段
            List<Long> chunkIds = searchResults.stream()
                    .map(MilvusService.SearchResult::getChunkId)
                    .collect(Collectors.toList());

            List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);
            
            // 按搜索结果的顺序排序，并附加分数信息
            Map<Long, Float> scoreMap = searchResults.stream()
                    .collect(Collectors.toMap(
                            MilvusService.SearchResult::getChunkId,
                            MilvusService.SearchResult::getScore
                    ));
            
            chunks.sort((a, b) -> {
                Float scoreA = scoreMap.getOrDefault(a.getId(), 0f);
                Float scoreB = scoreMap.getOrDefault(b.getId(), 0f);
                return scoreB.compareTo(scoreA); // 降序
            });

            log.debug("检索到{}个相关知识片段", chunks.size());
            // 将 scoreMap 附加到 chunks（通过创建包装对象或使用 Map）
            // 这里我们需要修改 buildSources 方法来接收 scoreMap
            return chunks;

        } catch (Exception e) {
            log.error("知识检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建上下文（包含文档元信息）
     */
    private String buildContext(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }

        // 批量查询所有涉及的文档信息
        Set<Long> docIds = chunks.stream()
                .map(KnowledgeChunk::getDocId)
                .collect(Collectors.toSet());
        
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            List<KnowledgeDoc> docs = docMapper.selectBatchIds(docIds);
            docMap = docs.stream()
                    .collect(Collectors.toMap(KnowledgeDoc::getId, doc -> doc));
        }

        StringBuilder context = new StringBuilder();
        int totalLength = 0;
        Long lastDocId = null;

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            KnowledgeDoc doc = docMap.get(chunk.getDocId());
            
            // 构建片段头部（包含文档元信息）
            StringBuilder chunkHeader = new StringBuilder();
            chunkHeader.append(String.format("[片段%d]", i + 1));
            
            // 只在文档切换时显示文档信息，避免重复
            if (doc != null && !chunk.getDocId().equals(lastDocId)) {
                chunkHeader.append(String.format(" 【文档：%s", doc.getTitle()));
                
                if (doc.getCategory() != null && !doc.getCategory().isEmpty()) {
                    chunkHeader.append(String.format(" | 分类：%s", doc.getCategory()));
                }
                
                if (chunk.getPageNumber() != null && chunk.getPageNumber() > 0) {
                    chunkHeader.append(String.format(" | 第%d页", chunk.getPageNumber()));
                }
                
                chunkHeader.append("】");
                lastDocId = chunk.getDocId();
            }
            
            chunkHeader.append("\n");
            String content = chunk.getContent();
            String fullChunk = chunkHeader.toString() + content;
            
            // 检查是否超过最大上下文长度
            if (totalLength + fullChunk.length() > maxContextLength) {
                // 截断
                int remaining = maxContextLength - totalLength;
                if (remaining > 150) { // 需要更多空间来容纳头部信息
                    int contentLength = remaining - chunkHeader.length() - 3; // 3为"..."的长度
                    if (contentLength > 50) {
                        content = content.substring(0, contentLength) + "...";
                        context.append(chunkHeader).append(content).append("\n\n");
                    }
                }
                break;
            }
            
            context.append(fullChunk).append("\n\n");
            totalLength += fullChunk.length();
        }

        return context.toString().trim();
    }

    @Override
    public String answerStream(String question, List<Message> historyMessages, Long userId, Long conversationId,
                               Consumer<String> statusConsumer,
                               Consumer<List<SourceInfo>> sourcesConsumer,
                               Consumer<String> contentConsumer) {
        log.info("RAG流式问答开始: question={}, userId={}, historyCount={}", question, userId, historyMessages.size());

        // 1. 状态更新：开始检索
        statusConsumer.accept("检索相关知识...");
        
        // 2. 检索相关知识片段
        List<KnowledgeChunk> relevantChunks = retrieve(question, defaultTopK);
        
        // 3. 状态更新：检索完成
        statusConsumer.accept("检索到 " + relevantChunks.size() + " 个相关片段");
        
        // 4. 发送来源信息（需要从 Milvus 重新获取分数）
        List<SourceInfo> sources = buildSourcesWithScores(relevantChunks, question);
        sourcesConsumer.accept(sources);
        
        // 5. 构建知识库上下文
        String context = buildContext(relevantChunks);
        
        // 6. 状态更新：开始生成
        statusConsumer.accept("正在生成回答...");
        
        // 7. 流式生成回答
        StringBuilder fullAnswer = new StringBuilder();
        llmService.ragAnswerStream(question, context, historyMessages, chunk -> {
            fullAnswer.append(chunk);
            contentConsumer.accept(chunk);
        });
        
        log.info("RAG流式问答完成: 检索到{}个片段, 回答长度={}", relevantChunks.size(), fullAnswer.length());
        
        return fullAnswer.toString();
    }

    /**
     * 构建来源信息（带准确的相似度分数）
     */
    private List<SourceInfo> buildSourcesWithScores(List<KnowledgeChunk> chunks, String question) {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 重新查询以获取分数
        Map<Long, Float> scoreMap = new HashMap<>();
        try {
            float[] queryVector = embeddingService.embed(question);
            if (queryVector != null && !allZeros(queryVector)) {
                List<MilvusService.SearchResult> searchResults = 
                        milvusService.search(queryVector, chunks.size(), 0f);
                scoreMap = searchResults.stream()
                        .collect(Collectors.toMap(
                                MilvusService.SearchResult::getChunkId,
                                MilvusService.SearchResult::getScore
                        ));
            }
        } catch (Exception e) {
            log.warn("获取相似度分数失败: {}", e.getMessage());
        }

        // 获取相关文档信息
        Set<Long> docIds = chunks.stream()
                .map(KnowledgeChunk::getDocId)
                .collect(Collectors.toSet());
        
        List<KnowledgeDoc> docs = docMapper.selectBatchIds(docIds);
        Map<Long, String> docTitleMap = docs.stream()
                .collect(Collectors.toMap(KnowledgeDoc::getId, KnowledgeDoc::getTitle));

        final Map<Long, Float> finalScoreMap = scoreMap;
        return chunks.stream()
                .map(chunk -> new SourceInfo(
                        chunk.getDocId(),
                        docTitleMap.getOrDefault(chunk.getDocId(), "未知文档"),
                        chunk.getId(),
                        truncateContent(chunk.getContent(), 200),
                        finalScoreMap.getOrDefault(chunk.getId(), 0f)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 构建来源信息
     */
    private List<SourceInfo> buildSources(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取相关文档信息
        Set<Long> docIds = chunks.stream()
                .map(KnowledgeChunk::getDocId)
                .collect(Collectors.toSet());
        
        List<KnowledgeDoc> docs = docMapper.selectBatchIds(docIds);
        Map<Long, String> docTitleMap = docs.stream()
                .collect(Collectors.toMap(KnowledgeDoc::getId, KnowledgeDoc::getTitle));

        return chunks.stream()
                .map(chunk -> new SourceInfo(
                        chunk.getDocId(),
                        docTitleMap.getOrDefault(chunk.getDocId(), "未知文档"),
                        chunk.getId(),
                        truncateContent(chunk.getContent(), 200),
                        0f // 分数可从搜索结果中获取
                ))
                .collect(Collectors.toList());
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 检查向量是否全为零
     */
    private boolean allZeros(float[] vector) {
        for (float v : vector) {
            if (v != 0) return false;
        }
        return true;
    }
}
