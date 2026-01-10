package com.echocampus.bot.service;

import com.echocampus.bot.entity.Message;
import com.echocampus.bot.service.RagService.RagResponse;
import com.echocampus.bot.service.RagService.SourceInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * 增强的RAG服务接口 - 支持上下文相关检索和AI自主判断
 */
public interface EnhancedRagService {
    
    /**
     * 智能问答（AI自主判断是否需要检索知识库）
     * 
     * @param question 用户问题
     * @param historyMessages 历史消息
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @return RAG响应
     */
    RagResponse answerWithAutoRetrieval(String question, List<Message> historyMessages, 
                                       Long userId, Long conversationId);
    
    /**
     * 智能流式问答（AI自主判断是否需要检索知识库）
     * 
     * @param question 用户问题
     * @param historyMessages 历史消息
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param statusConsumer 状态消费者
     * @param sourcesConsumer 来源消费者
     * @param contentConsumer 内容消费者
     * @return 完整回答
     */
    String answerWithAutoRetrievalStream(String question, List<Message> historyMessages,
                                        Long userId, Long conversationId,
                                        Consumer<String> statusConsumer,
                                        Consumer<List<SourceInfo>> sourcesConsumer,
                                        Consumer<String> contentConsumer);
}
