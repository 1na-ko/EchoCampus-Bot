package com.echocampus.bot.service.tool;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.service.EmbeddingService;
import com.echocampus.bot.service.VectorService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索工具 - LangChain4j Tool
 * 提供给AI模型自主调用的知识库检索功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final EmbeddingService embeddingService;
    private final VectorService vectorService;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocMapper docMapper;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    @Value("${rag.similarity-threshold:0.6}")
    private float similarityThreshold;

    /**
     * 在知识库中搜索相关信息
     * 
     * @param query 要搜索的问题或关键词
     * @return 格式化的知识库检索结果，包含相关文档片段
     */
    @Tool("在校园知识库中搜索相关信息。当用户询问关于学校、课程、活动、设施等校园相关问题时，使用此工具获取准确的知识库信息。")
    public String searchKnowledge(String query) {
        log.info("AI调用知识库检索工具: query={}", query);
        
        try {
            // 1. 将查询向量化
            float[] queryVector = embeddingService.embed(query);
            
            if (queryVector == null || allZeros(queryVector)) {
                log.warn("查询向量化失败: {}", query);
                return "知识库检索失败：无法向量化查询内容";
            }

            // 2. 在向量数据库中搜索相似向量
            List<VectorService.SearchResult> searchResults = 
                    vectorService.search(queryVector, defaultTopK, similarityThreshold);

            if (searchResults.isEmpty()) {
                log.info("未找到相关知识片段: query={}", query);
                return "知识库中未找到相关内容";
            }

            // 3. 根据chunkId获取完整的知识片段
            List<Long> chunkIds = searchResults.stream()
                    .map(VectorService.SearchResult::getChunkId)
                    .collect(Collectors.toList());

            List<KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds);
            
            // 4. 按搜索结果的顺序排序
            Map<Long, Float> scoreMap = searchResults.stream()
                    .collect(Collectors.toMap(
                            VectorService.SearchResult::getChunkId,
                            VectorService.SearchResult::getScore
                    ));
            
            chunks.sort((a, b) -> {
                Float scoreA = scoreMap.getOrDefault(a.getId(), 0f);
                Float scoreB = scoreMap.getOrDefault(b.getId(), 0f);
                return scoreB.compareTo(scoreA); // 降序
            });

            // 5. 构建格式化的知识库内容
            return formatKnowledgeResult(chunks, scoreMap);

        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage(), e);
            return "知识库检索出现错误：" + e.getMessage();
        }
    }

    /**
     * 格式化知识库检索结果
     */
    private String formatKnowledgeResult(List<KnowledgeChunk> chunks, Map<Long, Float> scoreMap) {
        if (chunks.isEmpty()) {
            return "知识库中未找到相关内容";
        }

        // 批量查询文档信息
        Set<Long> docIds = chunks.stream()
                .map(KnowledgeChunk::getDocId)
                .collect(Collectors.toSet());
        
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            List<KnowledgeDoc> docs = docMapper.selectBatchIds(docIds);
            docMap = docs.stream()
                    .collect(Collectors.toMap(KnowledgeDoc::getId, doc -> doc));
        }

        StringBuilder result = new StringBuilder();
        result.append("【知识库检索结果】\n");
        result.append(String.format("找到 %d 个相关内容片段：\n\n", chunks.size()));

        Long lastDocId = null;
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            KnowledgeDoc doc = docMap.get(chunk.getDocId());
            
            // 只在文档切换时显示文档信息
            if (doc != null && !chunk.getDocId().equals(lastDocId)) {
                result.append(String.format("--- 来源文档：%s", doc.getTitle()));
                if (doc.getCategory() != null && !doc.getCategory().isEmpty()) {
                    result.append(String.format("（分类：%s）", doc.getCategory()));
                }
                result.append(" ---\n");
                lastDocId = chunk.getDocId();
            }
            
            // 添加片段内容
            result.append(String.format("[片段%d] ", i + 1));
            if (chunk.getPageNumber() != null && chunk.getPageNumber() > 0) {
                result.append(String.format("(第%d页) ", chunk.getPageNumber()));
            }
            
            // 添加相似度分数
            Float score = scoreMap.get(chunk.getId());
            if (score != null) {
                result.append(String.format("[相关度:%.2f] ", score));
            }
            
            result.append("\n");
            result.append(chunk.getContent().trim());
            result.append("\n\n");
        }

        return result.toString();
    }

    /**
     * 检查向量是否全为0
     */
    private boolean allZeros(float[] vector) {
        for (float v : vector) {
            if (v != 0f) {
                return false;
            }
        }
        return true;
    }
}
