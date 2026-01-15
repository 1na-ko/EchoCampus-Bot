package com.echocampus.bot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志响应DTO
 * 用于返回操作日志信息，隐藏部分敏感字段
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作日志响应")
public class OperationLogResponse {

    /**
     * 日志ID
     */
    @Schema(description = "日志ID")
    private Long id;

    /**
     * 操作用户ID
     */
    @Schema(description = "操作用户ID")
    private Long userId;

    /**
     * 操作类型
     */
    @Schema(description = "操作类型")
    private String operationType;

    /**
     * 操作描述
     */
    @Schema(description = "操作描述")
    private String operationDesc;

    /**
     * 资源类型
     */
    @Schema(description = "资源类型")
    private String resourceType;

    /**
     * 资源ID
     */
    @Schema(description = "资源ID")
    private Long resourceId;

    /**
     * IP地址
     */
    @Schema(description = "用户IP地址")
    private String ipAddress;

    /**
     * 请求方法
     */
    @Schema(description = "HTTP请求方法")
    private String requestMethod;

    /**
     * 请求URL
     */
    @Schema(description = "请求URL")
    private String requestUrl;

    /**
     * 操作状态
     */
    @Schema(description = "操作状态：SUCCESS/FAILED")
    private String status;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息（失败时）")
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    @Schema(description = "执行耗时（毫秒）")
    private Long executionTime;

    /**
     * 操作时间
     */
    @Schema(description = "操作时间")
    private LocalDateTime createdAt;
}
