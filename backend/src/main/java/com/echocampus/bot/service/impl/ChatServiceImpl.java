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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
    public SseEmitter sendMessageStream(Long userId, ChatRequest request) {
        // 设置超时时间为60秒
        SseEmitter emitter = new SseEmitter(60000L);
        
        // 异步处理，避免阻塞请求
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 状态：思考中
                sendEvent(emitter, "status", "{\"phase\":\"thinking\",\"message\":\"思考中...\"}");
                
                // 1. 获取或创建会话
                Conversation conversation;
                if (request.getConversationId() != null) {
                    conversation = conversationMapper.selectById(request.getConversationId());
                    if (conversation == null) {
                        sendEvent(emitter, "error", "{\"message\":\"会话不存在\"}");
                        emitter.complete();
                        return;
                    }
                } else {
                    conversation = createConversation(userId, "新对话");
                }

                // 2. 保存用户消息
                Message userMessage = new Message();
                userMessage.setConversationId(conversation.getId());
                userMessage.setSenderType("USER");
                userMessage.setContent(request.getMessage());
                messageMapper.insert(userMessage);
                
                // 3. 获取历史消息（最近20条）
                List<Message> historyMessages = messageMapper.selectByConversationId(conversation.getId());
                List<Message> recentMessages = historyMessages.stream()
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .limit(20)
                        .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .collect(Collectors.toList());
                
                // 状态：检索知识
                sendEvent(emitter, "status", "{\"phase\":\"retrieving\",\"message\":\"检索知识...\"}");
                
                // 4. 调用RAG服务（流式）
                RagService.RagResponse ragResponse = ragService.answerStream(
                        request.getMessage(), 
                        recentMessages, 
                        userId, 
                        conversation.getId(),
                        // 状态回调：检索完成
                        (sources) -> {
                            try {
                                sendEvent(emitter, "status", "{\"phase\":\"generating\",\"message\":\"生成答案...\"}");
                                // 发送来源信息
                                if (sources != null && !sources.isEmpty()) {
                                    String sourcesJson = buildSourcesJson(sources);
                                    sendEvent(emitter, "sources", sourcesJson);
                                }
                            } catch (IOException e) {
                                log.error("发送检索状态失败", e);
                            }
                        },
                        // 内容回调：流式输出
                        (chunk) -> {
                            try {
                                sendEvent(emitter, "content", chunk);
                            } catch (IOException e) {
                                log.error("发送内容失败", e);
                            }
                        }
                );
                
                String aiAnswer = ragResponse.answer();
                
                // 5. 保存AI回复消息
                Message botMessage = new Message();
                botMessage.setConversationId(conversation.getId());
                botMessage.setParentMessageId(userMessage.getId());
                botMessage.setSenderType("BOT");
                botMessage.setContent(aiAnswer);
                messageMapper.insert(botMessage);

                // 6. 发送完成信号
                long responseTime = System.currentTimeMillis() - startTime;
                String doneJson = String.format(
                    "{\"conversationId\":%d,\"messageId\":%d,\"responseTimeMs\":%d}",
                    conversation.getId(), botMessage.getId(), responseTime
                );
                sendEvent(emitter, "done", doneJson);
                
                emitter.complete();
                
            } catch (Exception e) {
                log.error("流式处理失败", e);
                try {
                    sendEvent(emitter, "error", "{\"message\":\"" + e.getMessage() + "\"}");
                } catch (IOException ex) {
                    log.error("发送错误信息失败", ex);
                }
                emitter.completeWithError(e);
            }
        }).start();
        
        return emitter;
    }
    
    /**
     * 发送SSE事件
     */
    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
    }
    
    /**
     * 构建来源信息JSON
     */
    private String buildSourcesJson(List<RagService.SourceDoc> sources) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sources.size(); i++) {
            RagService.SourceDoc source = sources.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"docId\":%d,\"title\":\"%s\",\"content\":\"%s\",\"similarity\":%.4f}",
                source.docId(),
                escapeJson(source.docTitle()),
                escapeJson(source.content().substring(0, Math.min(100, source.content().length()))),
                source.score()
            ));
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 转义JSON字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
