package com.echocampus.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储提供者配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vector")
public class VectorProviderConfig {

    /**
     * 向量存储提供者类型: milvus, pgvector
     */
    private String provider = "pgvector";

    /**
     * 是否启用向量存储
     */
    private Boolean enabled = true;

    /**
     * 向量维度（通用配置）
     */
    private Integer dimension = 1024;

    /**
     * 判断是否使用pgvector
     */
    public boolean isPgVector() {
        return "pgvector".equalsIgnoreCase(provider);
    }

    /**
     * 判断是否使用Milvus
     */
    public boolean isMilvus() {
        return "milvus".equalsIgnoreCase(provider);
    }
}
