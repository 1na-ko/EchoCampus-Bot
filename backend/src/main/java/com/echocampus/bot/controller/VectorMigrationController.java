package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import com.echocampus.bot.service.VectorMigrationService;
import com.echocampus.bot.service.VectorMigrationService.MigrationProgress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 向量数据迁移控制器
 * 提供Milvus与pgvector之间的数据迁移管理API
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/migration")
@RequiredArgsConstructor
@Tag(name = "向量数据迁移", description = "Milvus与pgvector数据迁移管理接口")
public class VectorMigrationController {

    private final VectorMigrationService migrationService;

    @PostMapping("/milvus-to-pgvector")
    @Operation(summary = "从Milvus迁移到pgvector", description = "将向量数据从Milvus迁移到pgvector")
    public Result<String> migrateToToPgVector(
            @Parameter(description = "批量处理大小") @RequestParam(defaultValue = "50") int batchSize,
            @Parameter(description = "是否重建向量索引（推荐true）") @RequestParam(defaultValue = "true") boolean reindex) {
        
        log.info("收到迁移请求: Milvus -> pgvector, batchSize={}, reindex={}", batchSize, reindex);
        
        boolean started = migrationService.migrateFromMilvusToPgVector(batchSize, reindex);
        
        if (started) {
            return Result.success("迁移任务已启动，请通过进度查询接口跟踪状态");
        } else {
            return Result.error("迁移任务启动失败，可能已有任务在运行中");
        }
    }

    @PostMapping("/pgvector-to-milvus")
    @Operation(summary = "从pgvector迁移到Milvus", description = "将向量数据从pgvector迁移到Milvus（用于回滚）")
    public Result<String> migrateToMilvus(
            @Parameter(description = "批量处理大小") @RequestParam(defaultValue = "50") int batchSize) {
        
        log.info("收到迁移请求: pgvector -> Milvus, batchSize={}", batchSize);
        
        boolean started = migrationService.migrateFromPgVectorToMilvus(batchSize);
        
        if (started) {
            return Result.success("回滚迁移任务已启动");
        } else {
            return Result.error("回滚迁移任务启动失败");
        }
    }

    @GetMapping("/progress")
    @Operation(summary = "查询迁移进度", description = "获取当前迁移任务的进度信息")
    public Result<MigrationProgress> getMigrationProgress() {
        MigrationProgress progress = migrationService.getMigrationProgress();
        return Result.success(progress);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消迁移任务", description = "取消正在进行的迁移任务")
    public Result<String> cancelMigration() {
        boolean cancelled = migrationService.cancelMigration();
        
        if (cancelled) {
            return Result.success("迁移任务已取消");
        } else {
            return Result.error("取消失败，可能没有正在运行的任务");
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "验证迁移结果", description = "验证迁移后数据的完整性和正确性")
    public Result<Boolean> validateMigration() {
        boolean valid = migrationService.validateMigration();
        
        if (valid) {
            return Result.success("迁移验证通过", true);
        } else {
            Result<Boolean> result = Result.error("迁移验证失败，请检查日志");
            result.setData(false);
            return result;
        }
    }

    @DeleteMapping("/cleanup/{source}")
    @Operation(summary = "清理源数据", description = "迁移完成后清理源数据库中的向量数据（危险操作）")
    public Result<String> cleanupSource(
            @Parameter(description = "源类型: milvus 或 pgvector") @PathVariable String source,
            @Parameter(description = "确认清理") @RequestParam(defaultValue = "false") boolean confirm) {
        
        if (!confirm) {
            return Result.error("请设置 confirm=true 确认此危险操作");
        }
        
        log.warn("收到清理请求: source={}", source);
        
        boolean cleaned = migrationService.cleanupSource(source);
        
        if (cleaned) {
            return Result.success("源数据已清理");
        } else {
            return Result.error("清理失败");
        }
    }
}
