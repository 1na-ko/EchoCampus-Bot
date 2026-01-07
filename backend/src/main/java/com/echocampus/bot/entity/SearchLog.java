package com.echocampus.bot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检索日志实体类
 */
@Data
@TableName(value = "search_logs", autoResultMap = true)
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户查询
     */
    private String query;

    /**
     * 查询向量ID
     */
    private String queryVectorId;

    /**
     * 检索到的片段信息
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> retrievedChunks;

    /**
     * 响应时间（毫秒）
     */
    private Integer responseTimeMs;

    /**
     * 答案token数
     */
    private Integer answerTokens;

    /**
     * 状态：SUCCESS, FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
