package com.echocampus.bot.service;

import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.dto.response.StreamChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 发送消息并获取AI回复
     * @param userId 用户ID
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse sendMessage(Long userId, ChatRequest request);

    /**
     * 发送消息并获取流式AI回复
     * @param userId 用户ID
     * @param request 聊天请求
     * @param responseConsumer 流式响应消费者
     */
    void sendMessageStream(Long userId, ChatRequest request, Consumer<StreamChatResponse> responseConsumer);

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 会话列表
     */
    List<Conversation> getConversations(Long userId, Integer page, Integer size);

    /**
     * 获取会话的消息历史
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<Message> getMessages(Long conversationId);

    /**
     * 创建新会话
     * @param userId 用户ID
     * @param title 会话标题
     * @return 会话对象
     */
    Conversation createConversation(Long userId, String title);

    /**
     * 删除会话
     * @param conversationId 会话ID
     */
    void deleteConversation(Long conversationId);

    /**
     * 更新会话标题
     * @param conversationId 会话ID
     * @param title 新标题
     */
    void updateConversationTitle(Long conversationId, String title);
}
