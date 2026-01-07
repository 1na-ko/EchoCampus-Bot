package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.mapper.ConversationMapper;
import com.echocampus.bot.mapper.MessageMapper;
import com.echocampus.bot.service.ChatService;
import com.echocampus.bot.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final RagService ragService;

    @Override
    @Transactional
    public ChatResponse sendMessage(Long userId, ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        // 1. 获取或创建会话
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation == null) {
                throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
            }
        } else {
            // 创建新会话
            conversation = createConversation(userId, "新对话");
        }

        // 2. 保存用户消息
        Message userMessage = new Message();
        userMessage.setConversationId(conversation.getId());
        userMessage.setSenderType("USER");
        userMessage.setContent(request.getMessage());
        messageMapper.insert(userMessage);
        
        // 3. 调用RAG服务生成回复
        RagService.RagResponse ragResponse = ragService.answer(
                request.getMessage(), userId, conversation.getId());
                
        String aiAnswer = ragResponse.answer();
        List<ChatResponse.SourceDoc> sources = ragResponse.sources().stream()
                .map(s -> ChatResponse.SourceDoc.builder()
                        .docId(s.docId())
                        .title(s.docTitle())
                        .content(s.content())
                        .similarity(s.score())
                        .build())
                .collect(Collectors.toList());

        // 4. 保存AI回复消息
        Message botMessage = new Message();
        botMessage.setConversationId(conversation.getId());
        botMessage.setParentMessageId(userMessage.getId());
        botMessage.setSenderType("BOT");
        botMessage.setContent(aiAnswer);
        messageMapper.insert(botMessage);

        // 5. 构建响应
        long responseTime = System.currentTimeMillis() - startTime;
        
        return ChatResponse.builder()
                .messageId(botMessage.getId())
                .conversationId(conversation.getId())
                .answer(aiAnswer)
                .sources(sources)
                .usage(ChatResponse.TokenUsage.builder()
                        .promptTokens(0)
                        .completionTokens(0)
                        .totalTokens(0)
                        .build())
                .responseTimeMs(responseTime)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<Conversation> getConversations(Long userId, Integer page, Integer size) {
        return conversationMapper.selectRecentByUserId(userId, size);
    }

    @Override
    public List<Message> getMessages(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId);
    }

    @Override
    @Transactional
    public Conversation createConversation(Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setMessageCount(0);
        conversation.setStatus("ACTIVE");
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        conversation.setStatus("DELETED");
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public void updateConversationTitle(Long conversationId, String title) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        conversation.setTitle(title);
        conversationMapper.updateById(conversation);
    }
}
