package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import com.echocampus.bot.service.DataCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统管理控制器
 * <p>
 * 提供系统管理相关的接口，如数据清理等
 */
@Slf4j
@Tag(name = "System", description = "系统管理接口")
@RestController
@RequestMapping("/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final DataCleanupService dataCleanupService;

    /**
     * 手动触发数据清理
     * <p>
     * 立即执行一次数据清理任务，清理超过保留期限的软删除数据
     * 注意：生产环境建议添加权限控制
     */
    @Operation(summary = "手动触发数据清理", description = "立即执行一次数据清理任务")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cleanup")
    public Result<Map<String, Object>> triggerCleanup() {
        log.info("手动触发数据清理任务");
        
        long startTime = System.currentTimeMillis();
        int cleanedCount = dataCleanupService.cleanupExpiredDeletedData();
        long endTime = System.currentTimeMillis();
        
        Map<String, Object> result = new HashMap<>();
        result.put("cleanedCount", cleanedCount);
        result.put("duration", endTime - startTime);
        result.put("message", "数据清理完成");
        
        return Result.success(result);
    }
}
