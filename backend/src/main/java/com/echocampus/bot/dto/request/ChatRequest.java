package com.echocampus.bot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 聊天请求DTO
 */
@Data
public class ChatRequest {

    /**
     * 会话ID（可选，为空则创建新会话）
     */
    private Long conversationId;

    /**
     * 用户消息
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息长度不能超过2000字符")
    private String message;

    /**
     * 是否启用上下文（多轮对话）
     */
    private Boolean enableContext = true;

    /**
     * 上下文轮数（默认5轮）
     */
    private Integer contextRounds = 5;
}
