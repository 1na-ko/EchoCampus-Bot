package com.echocampus.bot.service;

import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.entity.Message;

import java.util.List;

/**
 * RAG检索增强生成服务接口
 */
public interface RagService {

    /**
     * RAG问答（支持多轮对话）
     *
     * @param question 用户问题
     * @param historyMessages 历史消息列表
     * @param userId 用户ID
     * @param conversationId 会话 ID（可选，用于多轮对话）
     * @return 问答响应
     */
    RagResponse answer(String question, List<Message> historyMessages, Long userId, Long conversationId);

    /**
     * 检索相关知识片段
     *
     * @param question 用户问题
     * @param topK 返回数量
     * @return 相关的知识片段列表
     */
    List<KnowledgeChunk> retrieve(String question, int topK);

    /**
     * RAG响应结果
     */
    record RagResponse(
            String answer,
            List<SourceInfo> sources,
            long responseTimeMs
    ) {}

    /**
     * 来源信息
     */
    record SourceInfo(
            Long docId,
            String docTitle,
            Long chunkId,
            String content,
            Float score
    ) {}
}
