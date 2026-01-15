package com.echocampus.bot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.echocampus.bot.annotation.OpLog;
import com.echocampus.bot.annotation.RequireRole;
import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.common.Result;
import com.echocampus.bot.dto.request.OperationLogQueryRequest;
import com.echocampus.bot.dto.response.OperationLogResponse;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志控制器
 * 提供操作日志的查询接口，支持按多种条件进行查询
 * 仅管理员可访问
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Tag(name = "OperationLog", description = "操作日志相关接口")
@RestController
@RequestMapping("/v1/admin/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     * 支持按用户ID、操作类型、资源类型、状态、时间范围、IP地址等条件进行查询
     *
     * @param request 查询请求参数
     * @return 分页查询结果
     */
    @Operation(summary = "分页查询操作日志", description = "支持多条件组合查询，仅管理员可访问")
    @PostMapping("/page")
    @RequireRole({"ADMIN"})
    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.SYSTEM,
            description = "查询操作日志列表",
            saveResponseResult = false
    )
    public Result<PageResult<OperationLogResponse>> queryPage(@RequestBody OperationLogQueryRequest request) {
        IPage<OperationLog> page = operationLogService.queryPage(
                request.getPage(),
                request.getSize(),
                request.getUserId(),
                request.getOperationType(),
                request.getResourceType(),
                request.getStatus(),
                request.getStartTime(),
                request.getEndTime(),
                request.getIpAddress()
        );

        // 转换为响应DTO
        List<OperationLogResponse> responseList = page.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        PageResult<OperationLogResponse> pageResult = PageResult.of(
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize(),
                responseList
        );

        return Result.success(pageResult);
    }

    /**
     * 根据ID查询操作日志详情
     *
     * @param id 日志ID
     * @return 操作日志详情
     */
    @Operation(summary = "查询操作日志详情", description = "根据ID查询操作日志的详细信息")
    @GetMapping("/{id}")
    @RequireRole({"ADMIN"})
    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.SYSTEM,
            description = "查询操作日志详情"
    )
    public Result<OperationLog> getById(
            @Parameter(description = "日志ID") @PathVariable Long id) {
        OperationLog log = operationLogService.getById(id);
        if (log == null) {
            return Result.error("操作日志不存在");
        }
        return Result.success(log);
    }

    /**
     * 查询指定用户的最近操作日志
     *
     * @param userId 用户ID
     * @param limit  限制数量（默认10条）
     * @return 操作日志列表
     */
    @Operation(summary = "查询用户最近操作日志", description = "查询指定用户的最近操作记录")
    @GetMapping("/user/{userId}/recent")
    @RequireRole({"ADMIN"})
    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.SYSTEM,
            description = "查询用户最近操作日志"
    )
    public Result<List<OperationLogResponse>> getRecentByUserId(
            @Parameter(description = "用户ID") @PathVariable Long userId,
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "10") Integer limit) {
        List<OperationLog> logs = operationLogService.getRecentByUserId(userId, limit);
        List<OperationLogResponse> responseList = logs.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return Result.success(responseList);
    }

    /**
     * 查询当前用户的操作日志
     * 普通用户可查看自己的操作记录
     *
     * @param httpRequest HTTP请求
     * @param limit       限制数量（默认20条）
     * @return 操作日志列表
     */
    @Operation(summary = "查询我的操作日志", description = "查询当前登录用户的最近操作记录")
    @GetMapping("/my")
    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.USER,
            description = "查询我的操作日志",
            saveResponseResult = false
    )
    public Result<List<OperationLogResponse>> getMyLogs(
            HttpServletRequest httpRequest,
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.error("用户未登录");
        }
        List<OperationLog> logs = operationLogService.getRecentByUserId(userId, limit);
        List<OperationLogResponse> responseList = logs.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return Result.success(responseList);
    }

    /**
     * 查询用户最后登录记录
     *
     * @param userId 用户ID
     * @return 最后登录的操作日志
     */
    @Operation(summary = "查询用户最后登录记录", description = "查询指定用户的最后一次登录记录")
    @GetMapping("/user/{userId}/last-login")
    @RequireRole({"ADMIN"})
    public Result<OperationLogResponse> getLastLogin(
            @Parameter(description = "用户ID") @PathVariable Long userId) {
        OperationLog log = operationLogService.getLastLoginByUserId(userId);
        if (log == null) {
            return Result.error("未找到登录记录");
        }
        return Result.success(convertToResponse(log));
    }

    /**
     * 将实体转换为响应DTO
     * 隐藏敏感信息，如请求参数和响应结果
     *
     * @param log 操作日志实体
     * @return 响应DTO
     */
    private OperationLogResponse convertToResponse(OperationLog log) {
        return OperationLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .operationType(log.getOperationType())
                .operationDesc(log.getOperationDesc())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .ipAddress(log.getIpAddress())
                .requestMethod(log.getRequestMethod())
                .requestUrl(log.getRequestUrl())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .executionTime(log.getExecutionTime())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
