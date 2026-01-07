package com.echocampus.bot.service;

import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.entity.Message;

import java.util.List;
import java.util.function.Consumer;

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
     * RAG问答（流式输出）
     *
     * @param question 用户问题
     * @param historyMessages 历史消息列表
     * @param userId 用户ID
     * @param conversationId 会话 ID
     * @param onRetrieved 检索完成回调
     * @param onContent 内容块回调
     * @return 问答响应
     */
    RagResponse answerStream(String question, List<Message> historyMessages, Long userId, Long conversationId,
                             Consumer<List<SourceDoc>> onRetrieved, Consumer<String> onContent);

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

    /**
     * 来源文档（简化版）
     */
    record SourceDoc(
            Long docId,
            String docTitle,
            String content,
            Float score
    ) {}
}
