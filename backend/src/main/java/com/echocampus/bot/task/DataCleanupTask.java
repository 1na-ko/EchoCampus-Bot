package com.echocampus.bot.task;

import com.echocampus.bot.service.DataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据清理定时任务
 * <p>
 * 定期清理超过保留期限的软删除数据，释放数据库空间
 * 通过配置项 data-cleanup.enabled 控制是否启用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "data-cleanup", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DataCleanupTask {

    private final DataCleanupService dataCleanupService;

    /**
     * 执行数据清理任务
     * <p>
     * 通过配置项 data-cleanup.cron 指定执行时间
     * 默认每天凌晨3点执行
     */
    @Scheduled(cron = "${data-cleanup.cron:0 0 3 * * ?}")
    public void executeCleanup() {
        log.info("===== 开始执行数据清理定时任务 =====");
        
        long startTime = System.currentTimeMillis();
        
        try {
            int cleanedCount = dataCleanupService.cleanupExpiredDeletedData();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            log.info("===== 数据清理定时任务执行完成，耗时: {} ms，清理记录数: {} =====", duration, cleanedCount);
        } catch (Exception e) {
            log.error("===== 数据清理定时任务执行失败 =====", e);
        }
    }
}
