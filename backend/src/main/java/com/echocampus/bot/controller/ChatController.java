package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天控制器
 */
@Tag(name = "Chat", description = "聊天相关接口")
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "发送消息", description = "发送消息并获取AI回复")
    @PostMapping("/message")
    public Result<ChatResponse> sendMessage(
            @Parameter(description = "用户ID", required = true) @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.sendMessage(userId, request);
        return Result.success(response);
    }

    @Operation(summary = "发送消息（流式）", description = "发送消息并以SSE流式方式获取AI回复")
    @GetMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(
            @Parameter(description = "用户ID", required = true) @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Parameter(description = "问题内容") @RequestParam String message,
            @Parameter(description = "会话ID") @RequestParam(required = false) Long conversationId) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setConversationId(conversationId);
        return chatService.sendMessageStream(userId, request);
    }

    @Operation(summary = "获取会话列表", description = "获取用户的会话列表")
    @GetMapping("/conversations")
    public Result<List<Conversation>> getConversations(
            @Parameter(description = "用户ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        List<Conversation> conversations = chatService.getConversations(userId, page, size);
        return Result.success(conversations);
    }

    @Operation(summary = "获取会话消息", description = "获取指定会话的消息历史")
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<Message>> getMessages(
            @Parameter(description = "会话ID") @PathVariable Long conversationId) {
        List<Message> messages = chatService.getMessages(conversationId);
        return Result.success(messages);
    }

    @Operation(summary = "创建新会话", description = "创建一个新的对话会话")
    @PostMapping("/conversations")
    public Result<Conversation> createConversation(
            @Parameter(description = "用户ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Parameter(description = "会话标题") @RequestParam(defaultValue = "新对话") String title) {
        Conversation conversation = chatService.createConversation(userId, title);
        return Result.success(conversation);
    }

    @Operation(summary = "删除会话", description = "删除指定的对话会话")
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID") @PathVariable Long conversationId) {
        chatService.deleteConversation(conversationId);
        return Result.success();
    }

    @Operation(summary = "更新会话标题", description = "更新对话会话的标题")
    @PutMapping("/conversations/{conversationId}")
    public Result<Void> updateConversationTitle(
            @Parameter(description = "会话ID") @PathVariable Long conversationId,
            @Parameter(description = "新标题") @RequestParam String title) {
        chatService.updateConversationTitle(conversationId, title);
        return Result.success();
    }
}
