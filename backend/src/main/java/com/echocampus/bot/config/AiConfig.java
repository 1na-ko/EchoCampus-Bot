package com.echocampus.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI服务配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private EmbeddingConfig embedding = new EmbeddingConfig();
    private LlmConfig llm = new LlmConfig();

    @Data
    public static class EmbeddingConfig {
        /** API密钥 */
        private String apiKey;
        /** API地址 */
        private String apiUrl;
        /** 模型名称 */
        private String model;
        /** 向量维度 */
        private Integer dimension = 1536;
        /** 批量处理大小 */
        private Integer batchSize = 10;
        /** 最大重试次数 */
        private Integer maxRetries = 3;
    }

    @Data
    public static class LlmConfig {
        /** API密钥 */
        private String apiKey;
        /** API地址 */
        private String apiUrl;
        /** 模型名称 */
        private String model;
        /** 最大Token数 */
        private Integer maxTokens = 2000;
        /** 温度参数 */
        private Double temperature = 0.7;
        /** 超时时间(秒) */
        private Integer timeout = 60;
    }
}
