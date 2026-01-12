package com.echocampus.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI服务配置（Embedding + LLM）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiServiceConfig {

    /**
     * Embedding服务配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * LLM服务配置
     */
    private LlmConfig llm = new LlmConfig();

    @Data
    public static class EmbeddingConfig {
        /**
         * API Key
         */
        private String apiKey;

        /**
         * API URL
         */
        private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

        /**
         * 模型名称
         */
        private String model = "text-embedding-v3";

        /**
         * 向量维度
         */
        private Integer dimension = 1024;

        /**
         * 批量处理大小
         */
        private Integer batchSize = 10;

        /**
         * 最大重试次数
         */
        private Integer maxRetries = 3;
    }

    @Data
    public static class LlmConfig {
        /**
         * API Key
         */
        private String apiKey;

        /**
         * API URL
         */
        private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

        /**
         * 模型名称
         */
        private String model = "deepseek-chat";

        /**
         * 最大Token数
         */
        private Integer maxTokens = 2000;

        /**
         * 温度参数
         */
        private Double temperature = 0.7;

        /**
         * 超时时间（秒）
         */
        private Integer timeout = 60;
    }
}
