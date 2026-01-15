package com.echocampus.bot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志查询请求DTO
 * 用于接收操作日志查询的请求参数
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "操作日志查询请求")
public class OperationLogQueryRequest {

    /**
     * 当前页码
     */
    @Schema(description = "当前页码", example = "1", defaultValue = "1")
    private Integer page;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "20", defaultValue = "20")
    private Integer size;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID，用于查询指定用户的操作日志")
    private Long userId;

    /**
     * 操作类型
     * @see com.echocampus.bot.entity.OperationLog.OperationType
     */
    @Schema(description = "操作类型", example = "LOGIN", 
            allowableValues = {"LOGIN", "LOGOUT", "REGISTER", "CREATE", "UPDATE", "DELETE", "QUERY", "UPLOAD", "DOWNLOAD", "SEND_CODE", "CHANGE_PASSWORD", "CHAT", "OTHER"})
    private String operationType;

    /**
     * 资源类型
     * @see com.echocampus.bot.entity.OperationLog.ResourceType
     */
    @Schema(description = "资源类型", example = "USER",
            allowableValues = {"USER", "DOC", "CHUNK", "CONFIG", "CONVERSATION", "MESSAGE", "CATEGORY", "VERIFICATION_CODE", "SYSTEM"})
    private String resourceType;

    /**
     * 操作状态
     */
    @Schema(description = "操作状态", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED"})
    private String status;

    /**
     * 开始时间
     */
    @Schema(description = "查询开始时间", example = "2024-01-01T00:00:00")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "查询结束时间", example = "2024-12-31T23:59:59")
    private LocalDateTime endTime;

    /**
     * IP地址（支持模糊匹配）
     */
    @Schema(description = "IP地址，支持模糊匹配", example = "192.168")
    private String ipAddress;
}
