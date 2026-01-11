package com.echocampus.bot.controller;

import com.echocampus.bot.annotation.RequireAuth;
import com.echocampus.bot.common.Result;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.config.RateLimitConfig;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.dto.response.StreamChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 聊天控制器
 */
@Slf4j
@Tag(name = "Chat", description = "聊天相关接口")
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor sseExecutor;
    private final RateLimitConfig.RateLimiter rateLimiter;

    @Operation(summary = "发送消息", description = "发送消息并获取AI回复")
    @PostMapping("/message")
    @RequireAuth
    public Result<ChatResponse> sendMessage(HttpServletRequest request, @Valid @RequestBody ChatRequest chatRequest) {
        Long userId = (Long) request.getAttribute("userId");
        ChatResponse response = chatService.sendMessage(userId, chatRequest);
        return Result.success(response);
    }

    @Operation(summary = "发送消息（流式）", description = "发送消息并获取流式AI回复")
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireAuth
    public SseEmitter sendMessageStream(HttpServletRequest request, @Valid @RequestBody ChatRequest chatRequest) {
        Long userId = (Long) request.getAttribute("userId");
        
        // 限流检查
        if (!rateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ResultCode.SYSTEM_BUSY, "系统繁忙，请稍后再试");
        }
        
        // 创建SSE发射器，超时时间5分钟
        SseEmitter emitter = new SseEmitter(300000L);
        
        // 异步执行流式响应
        sseExecutor.execute(() -> {
            try {
                chatService.sendMessageStream(userId, chatRequest, streamResponse -> {
                    try {
                        String json = objectMapper.writeValueAsString(streamResponse);
                        emitter.send(SseEmitter.event()
                                .name(streamResponse.getType().name().toLowerCase())
                                .data(json));
                    } catch (IOException e) {
                        log.error("SSE发送失败: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                log.error("流式响应异常: {}", e.getMessage(), e);
                try {
                    StreamChatResponse errorResponse = StreamChatResponse.error(
                            chatRequest.getConversationId(), null, e.getMessage());
                    String json = objectMapper.writeValueAsString(errorResponse);
                    emitter.send(SseEmitter.event().name("error").data(json));
                } catch (IOException ex) {
                    log.error("发送错误事件失败: {}", ex.getMessage());
                }
                emitter.completeWithError(e);
            }
        });
        
        // 设置完成和超时回调
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成");
            rateLimiter.release(userId);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
            rateLimiter.release(userId);
        });
        emitter.onError(e -> {
            log.error("SSE连接错误: {}", e.getMessage());
            rateLimiter.release(userId);
        });
        
        return emitter;
    }

    @Operation(summary = "获取会话列表", description = "获取用户的会话列表")
    @GetMapping("/conversations")
    @RequireAuth
    public Result<List<Conversation>> getConversations(HttpServletRequest request,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        List<Conversation> conversations = chatService.getConversations(userId, page, size);
        return Result.success(conversations);
    }

    @Operation(summary = "获取会话消息", description = "获取指定会话的消息历史")
    @GetMapping("/conversations/{conversationId}/messages")
    @RequireAuth
    public Result<List<Message>> getMessages(HttpServletRequest request, @Parameter(description = "会话ID") @PathVariable Long conversationId) {
        Long userId = (Long) request.getAttribute("userId");
        List<Message> messages = chatService.getMessages(conversationId);
        return Result.success(messages);
    }

    @Operation(summary = "创建新会话", description = "创建一个新的对话会话")
    @PostMapping("/conversations")
    @RequireAuth
    public Result<Conversation> createConversation(HttpServletRequest request,
            @Parameter(description = "会话标题") @RequestParam(defaultValue = "新对话") String title) {
        Long userId = (Long) request.getAttribute("userId");
        Conversation conversation = chatService.createConversation(userId, title);
        return Result.success(conversation);
    }

    @Operation(summary = "删除会话", description = "删除指定的对话会话")
    @DeleteMapping("/conversations/{conversationId}")
    @RequireAuth
    public Result<Void> deleteConversation(HttpServletRequest request, @Parameter(description = "会话ID") @PathVariable Long conversationId) {
        Long userId = (Long) request.getAttribute("userId");
        chatService.deleteConversation(conversationId);
        return Result.success();
    }

    @Operation(summary = "更新会话标题", description = "更新对话会话的标题")
    @PutMapping("/conversations/{conversationId}")
    @RequireAuth
    public Result<Void> updateConversationTitle(HttpServletRequest request,
            @Parameter(description = "会话ID") @PathVariable Long conversationId,
            @Parameter(description = "新标题") @RequestParam String title) {
        Long userId = (Long) request.getAttribute("userId");
        chatService.updateConversationTitle(conversationId, title);
        return Result.success();
    }
}
