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
        
        // 4. 构建来源信息
        List<SourceInfo> sources = buildSources(relevantChunks);
        
        long responseTime = System.currentTimeMillis() - startTime;
        log.info("RAG问答完成: 耗时={}ms, 检索到{}个片段", responseTime, relevantChunks.size());

        return new RagResponse(answer, sources, responseTime);
    }

    @Override
    public RagResponse answerStream(String question, List<Message> historyMessages, Long userId, Long conversationId,
                                     Consumer<List<SourceDoc>> onRetrieved, Consumer<String> onContent) {
        long startTime = System.currentTimeMillis();
        
        log.info("RAG流式问答开始: question={}, userId={}, historyCount={}", question, userId, historyMessages.size());

        // 1. 检索相关知识片段
        List<KnowledgeChunk> relevantChunks = retrieve(question, defaultTopK);
        
        // 2. 构建知识库上下文
        String context = buildContext(relevantChunks);
        
        // 3. 回调：检索完成，发送来源信息
        if (onRetrieved != null) {
            List<SourceDoc> sourceDocs = buildSourceDocs(relevantChunks);
            onRetrieved.accept(sourceDocs);
        }
        
        // 4. 生成回答（流式）
        String answer = llmService.ragAnswerStream(question, context, historyMessages, onContent);
        
        // 5. 构建来源信息
        List<SourceInfo> sources = buildSources(relevantChunks);
        
        long responseTime = System.currentTimeMillis() - startTime;
        log.info("RAG流式问答完成: 耗时={}ms, 检索到{}个片段", responseTime, relevantChunks.size());

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
            return chunks;

        } catch (Exception e) {
            log.error("知识检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建上下文
     */
    private String buildContext(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int totalLength = 0;

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            String content = chunk.getContent();
            
            // 检查是否超过最大上下文长度
            if (totalLength + content.length() > maxContextLength) {
                // 截断
                int remaining = maxContextLength - totalLength;
                if (remaining > 100) {
                    content = content.substring(0, remaining) + "...";
                    context.append(String.format("[%d] %s\n\n", i + 1, content));
                }
                break;
            }
            
            context.append(String.format("[%d] %s\n\n", i + 1, content));
            totalLength += content.length();
        }

        return context.toString().trim();
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
     * 构建来源文档（简化版）
     */
    private List<SourceDoc> buildSourceDocs(List<KnowledgeChunk> chunks) {
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

        // 按文档分组，合并同一文档的内容
        Map<Long, List<KnowledgeChunk>> docChunksMap = chunks.stream()
                .collect(Collectors.groupingBy(KnowledgeChunk::getDocId));

        return docChunksMap.entrySet().stream()
                .map(entry -> {
                    Long docId = entry.getKey();
                    List<KnowledgeChunk> docChunks = entry.getValue();
                    String combinedContent = docChunks.stream()
                            .map(KnowledgeChunk::getContent)
                            .collect(Collectors.joining("\n\n"));
                    return new SourceDoc(
                            docId,
                            docTitleMap.getOrDefault(docId, "未知文档"),
                            truncateContent(combinedContent, 300),
                            0f // 分数可从搜索结果中获取
                    );
                })
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
