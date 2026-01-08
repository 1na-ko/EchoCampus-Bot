package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.dto.response.StreamChatResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
        
        // 3. 获取历史消息（最近N轮对话）
        List<Message> historyMessages = messageMapper.selectByConversationId(conversation.getId());
        // 按时间排序，只取最近10轮对话（20条消息）
        List<Message> recentMessages = historyMessages.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
        
        // 4. 调用RAG服务生成回复（携带历史消息）
        RagService.RagResponse ragResponse = ragService.answer(
                request.getMessage(), recentMessages, userId, conversation.getId());
                
        String aiAnswer = ragResponse.answer();
        List<ChatResponse.SourceDoc> sources = ragResponse.sources().stream()
                .map(s -> ChatResponse.SourceDoc.builder()
                        .docId(s.docId())
                        .title(s.docTitle())
                        .content(s.content())
                        .similarity(s.score())
                        .build())
                .collect(Collectors.toList());

        // 5. 保存AI回复消息
        Message botMessage = new Message();
        botMessage.setConversationId(conversation.getId());
        botMessage.setParentMessageId(userMessage.getId());
        botMessage.setSenderType("BOT");
        botMessage.setContent(aiAnswer);
        messageMapper.insert(botMessage);

        // 6. 构建响应
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
    @Transactional
    public void sendMessageStream(Long userId, ChatRequest request, Consumer<StreamChatResponse> responseConsumer) {
        long startTime = System.currentTimeMillis();
        
        // 1. 获取或创建会话
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation == null) {
                throw new BusinessException(ResultCode.CONVERSATION_NOT_FOUND);
            }
        } else {
            // 创建新会话，使用问题内容作为标题
            String title = request.getMessage();
            if (title.length() > 50) {
                title = title.substring(0, 50) + "...";
            }
            conversation = createConversation(userId, title);
        }
        
        final Long conversationId = conversation.getId();

        // 2. 保存用户消息
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setSenderType("USER");
        userMessage.setContent(request.getMessage());
        messageMapper.insert(userMessage);
        
        // 3. 创建AI回复消息（先保存空消息，获取ID）
        Message botMessage = new Message();
        botMessage.setConversationId(conversationId);
        botMessage.setParentMessageId(userMessage.getId());
        botMessage.setSenderType("BOT");
        botMessage.setContent(""); // 先设为空，后续更新
        messageMapper.insert(botMessage);
        
        final Long messageId = botMessage.getId();
        
        // 4. 发送状态：开始处理
        responseConsumer.accept(StreamChatResponse.status(conversationId, messageId, "正在处理您的问题..."));
        
        // 5. 获取历史消息（最近N轮对话，排除刚插入的消息）
        List<Message> historyMessages = messageMapper.selectByConversationId(conversationId);
        List<Message> recentMessages = historyMessages.stream()
                .filter(m -> !m.getId().equals(userMessage.getId()) && !m.getId().equals(messageId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());
        
        // 6. 用于收集完整回答的StringBuilder和知识来源
        StringBuilder fullAnswer = new StringBuilder();
        List<ChatResponse.SourceDoc> allSourceDocs = new ArrayList<>();
        
        // 7. 调用RAG流式服务
        String answer = ragService.answerStream(
                request.getMessage(),
                recentMessages,
                userId,
                conversationId,
                // 状态消费者
                status -> responseConsumer.accept(
                        StreamChatResponse.status(conversationId, messageId, status)),
                // 来源消费者
                sources -> {
                    List<ChatResponse.SourceDoc> sourceDocs = sources.stream()
                            .map(s -> ChatResponse.SourceDoc.builder()
                                    .docId(s.docId())
                                    .title(s.docTitle())
                                    .content(s.content())
                                    .similarity(s.score())
                                    .build())
                            .collect(Collectors.toList());
                    allSourceDocs.addAll(sourceDocs);
                    responseConsumer.accept(
                            StreamChatResponse.sources(conversationId, messageId, sourceDocs));
                },
                // 内容消费者
                chunk -> {
                    fullAnswer.append(chunk);
                    responseConsumer.accept(
                            StreamChatResponse.content(conversationId, messageId, chunk));
                }
        );
        
        // 8. 更新AI消息内容和元数据
        // 保存 metadata（包括 sources）
        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> sourcesData = allSourceDocs.stream()
                .map(source -> {
                    Map<String, Object> sourceMap = new HashMap<>();
                    sourceMap.put("docId", source.getDocId());
                    sourceMap.put("title", source.getTitle());
                    sourceMap.put("content", source.getContent());
                    sourceMap.put("similarity", source.getSimilarity());
                    return sourceMap;
                })
                .collect(Collectors.toList());
        metadata.put("sources", sourcesData);
        
        // 使用自定义方法更新，处理 JSONB 类型
        messageMapper.updateContentAndMetadata(botMessage.getId(), fullAnswer.toString(), metadata);
        
        // 9. 发送完成事件
        long responseTime = System.currentTimeMillis() - startTime;
        responseConsumer.accept(StreamChatResponse.done(
                conversationId,
                messageId,
                ChatResponse.TokenUsage.builder()
                        .promptTokens(0)
                        .completionTokens(0)
                        .totalTokens(0)
                        .build(),
                responseTime
        ));
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
