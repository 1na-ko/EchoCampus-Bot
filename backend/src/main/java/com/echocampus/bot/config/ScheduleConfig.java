package com.echocampus.bot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置类
 * <p>
 * 启用Spring的定时任务功能
 * 通过配置项 data-cleanup.enabled 控制是否启用
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "data-cleanup", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ScheduleConfig {
    // Spring会自动扫描带有@Scheduled注解的方法
}
