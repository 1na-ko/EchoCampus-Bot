package com.echocampus.bot.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * AI回答内容
     */
    private String answer;

    /**
     * 相关知识来源
     */
    private List<SourceDoc> sources;

    /**
     * Token使用统计
     */
    private TokenUsage usage;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTimeMs;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 知识来源文档
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDoc {
        /**
         * 文档ID
         */
        private Long docId;

        /**
         * 文档标题
         */
        private String title;

        /**
         * 相关内容片段
         */
        private String content;

        /**
         * 相似度得分
         */
        private Float similarity;

        /**
         * 分类
         */
        private String category;
    }

    /**
     * Token使用统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        /**
         * Prompt Token数
         */
        private Integer promptTokens;

        /**
         * 生成Token数
         */
        private Integer completionTokens;

        /**
         * 总Token数
         */
        private Integer totalTokens;
    }
}
