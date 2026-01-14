package com.echocampus.bot.service;

import java.util.List;

/**
 * 向量数据库服务统一接口
 * 支持多种向量存储后端（Milvus、pgvector等）
 */
public interface VectorService {

    /**
     * 初始化向量存储（创建表/集合、索引等）
     */
    void initVectorStore();

    /**
     * 插入向量
     * @param vectors 向量列表
     * @param chunkIds 对应的chunk ID列表
     * @param docIds 对应的文档ID列表
     * @param contents 对应的文本内容列表
     * @param categories 对应的分类列表
     * @return 向量ID列表
     */
    List<String> insertVectors(List<float[]> vectors, List<Long> chunkIds, List<Long> docIds, 
                                List<String> contents, List<String> categories);

    /**
     * 搜索相似向量
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param threshold 相似度阈值
     * @return 搜索结果列表
     */
    List<SearchResult> search(float[] queryVector, int topK, float threshold);

    /**
     * 删除向量
     * @param vectorIds 向量ID列表
     */
    void deleteVectors(List<String> vectorIds);

    /**
     * 删除文档相关的所有向量
     * @param docId 文档ID
     */
    void deleteByDocId(Long docId);

    /**
     * 获取向量数量
     * @return 向量数量
     */
    long getVectorCount();

    /**
     * 检查向量服务是否可用
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取向量存储提供者名称
     * @return 提供者名称
     */
    String getProviderName();

    /**
     * 搜索结果类
     */
    class SearchResult {
        private String vectorId;
        private Long chunkId;
        private Long docId;
        private String content;
        private String category;
        private Float score;

        public SearchResult() {}

        public SearchResult(String vectorId, Long chunkId, Long docId, String content, String category, Float score) {
            this.vectorId = vectorId;
            this.chunkId = chunkId;
            this.docId = docId;
            this.content = content;
            this.category = category;
            this.score = score;
        }

        // Getters and Setters
        public String getVectorId() { return vectorId; }
        public void setVectorId(String vectorId) { this.vectorId = vectorId; }
        public Long getChunkId() { return chunkId; }
        public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
        public Long getDocId() { return docId; }
        public void setDocId(Long docId) { this.docId = docId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public Float getScore() { return score; }
        public void setScore(Float score) { this.score = score; }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "vectorId='" + vectorId + '\'' +
                    ", chunkId=" + chunkId +
                    ", docId=" + docId +
                    ", score=" + score +
                    '}';
        }
    }
}
