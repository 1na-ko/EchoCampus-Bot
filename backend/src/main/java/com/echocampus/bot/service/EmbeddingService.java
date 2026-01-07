package com.echocampus.bot.service;

import java.util.List;

/**
 * Embedding向量化服务接口
 */
public interface EmbeddingService {

    /**
     * 将单个文本转换为向量
     *
     * @param text 文本内容
     * @return 向量数组
     */
    float[] embed(String text);

    /**
     * 批量将文本转换为向量
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     *
     * @return 维度
     */
    int getDimension();

    /**
     * 检查服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}
