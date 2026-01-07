package com.echocampus.bot.service;

import java.util.List;

/**
 * Milvus向量数据库服务接口
 */
public interface MilvusService {

    /**
     * 初始化集合（如果不存在则创建）
     */
    void initCollection();

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
     * 获取集合中的向量数量
     * @return 向量数量
     */
    long getVectorCount();

    /**
     * 检查Milvus服务是否可用
     * @return 是否可用
     */
    boolean isAvailable();

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
    }
}
