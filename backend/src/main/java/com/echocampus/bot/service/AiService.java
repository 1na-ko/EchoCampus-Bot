package com.echocampus.bot.service;

import java.util.List;

/**
 * AI服务接口（Embedding + LLM）
 */
public interface AiService {

    /**
     * 获取文本的向量表示
     * @param text 文本
     * @return 向量数组
     */
    float[] getTextEmbedding(String text);

    /**
     * 批量获取文本的向量表示
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> getTextEmbeddings(List<String> texts);

    /**
     * 生成AI回答
     * @param prompt 提示词
     * @return AI生成的回答
     */
    String generateAnswer(String prompt);

    /**
     * 构建RAG提示词
     * @param context 检索到的上下文
     * @param question 用户问题
     * @param conversationHistory 对话历史
     * @return 构建的提示词
     */
    String buildRagPrompt(String context, String question, List<String> conversationHistory);
}
