package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import com.echocampus.bot.utils.DateTimeUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@Tag(name = "Health", description = "健康检查接口")
@RestController
@RequestMapping("/v1")
public class HealthController {

    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", DateTimeUtil.now());
        health.put("version", "1.0.0");
        health.put("application", "EchoCampus-Bot");
        return Result.success(health);
    }

    @Operation(summary = "服务信息", description = "获取服务基本信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "EchoCampus-Bot");
        info.put("description", "基于RAG技术的智能校园问答系统");
        info.put("version", "1.0.0");
        info.put("author", "EchoCampus Team");
        return Result.success(info);
    }
}
