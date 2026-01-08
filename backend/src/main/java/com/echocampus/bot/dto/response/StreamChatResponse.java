package com.echocampus.bot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流式聊天响应DTO（SSE事件）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChatResponse {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 内容片段（当type为CONTENT时）
     */
    private String content;

    /**
     * 处理阶段（当type为STATUS时）
     */
    private String stage;

    /**
     * 来源文档（当type为SOURCES时）
     */
    private List<ChatResponse.SourceDoc> sources;

    /**
     * Token使用统计（当type为DONE时）
     */
    private ChatResponse.TokenUsage usage;

    /**
     * 响应时间（毫秒，当type为DONE时）
     */
    private Long responseTimeMs;

    /**
     * 错误信息（当type为ERROR时）
     */
    private String error;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 状态更新（检索中、生成中等）
         */
        STATUS,
        /**
         * 知识来源信息
         */
        SOURCES,
        /**
         * 内容片段
         */
        CONTENT,
        /**
         * 流结束
         */
        DONE,
        /**
         * 错误
         */
        ERROR
    }

    // 工厂方法
    public static StreamChatResponse status(Long conversationId, Long messageId, String stage) {
        return StreamChatResponse.builder()
                .type(EventType.STATUS)
                .conversationId(conversationId)
                .messageId(messageId)
                .stage(stage)
                .build();
    }

    public static StreamChatResponse sources(Long conversationId, Long messageId, List<ChatResponse.SourceDoc> sources) {
        return StreamChatResponse.builder()
                .type(EventType.SOURCES)
                .conversationId(conversationId)
                .messageId(messageId)
                .sources(sources)
                .build();
    }

    public static StreamChatResponse content(Long conversationId, Long messageId, String content) {
        return StreamChatResponse.builder()
                .type(EventType.CONTENT)
                .conversationId(conversationId)
                .messageId(messageId)
                .content(content)
                .build();
    }

    public static StreamChatResponse done(Long conversationId, Long messageId, ChatResponse.TokenUsage usage, Long responseTimeMs) {
        return StreamChatResponse.builder()
                .type(EventType.DONE)
                .conversationId(conversationId)
                .messageId(messageId)
                .usage(usage)
                .responseTimeMs(responseTimeMs)
                .build();
    }

    public static StreamChatResponse error(Long conversationId, Long messageId, String error) {
        return StreamChatResponse.builder()
                .type(EventType.ERROR)
                .conversationId(conversationId)
                .messageId(messageId)
                .error(error)
                .build();
    }
}
