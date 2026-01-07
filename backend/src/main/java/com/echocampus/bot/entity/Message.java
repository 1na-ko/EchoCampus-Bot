package com.echocampus.bot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对话消息实体类
 */
@Data
@TableName(value = "messages", autoResultMap = true)
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 父消息ID（用于多轮对话关联）
     */
    private Long parentMessageId;

    /**
     * 发送者类型：USER, BOT, SYSTEM
     */
    private String senderType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 元数据（JSON格式存储额外信息）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
