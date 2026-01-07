package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.AiConfig;
import com.echocampus.bot.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼平台 Embedding服务实现
 * 使用 text-embedding-v3 模型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("输入文本为空，返回零向量");
            return new float[getDimension()];
        }

        List<float[]> results = embedBatch(Collections.singletonList(text));
        return results.isEmpty() ? new float[getDimension()] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> allEmbeddings = new ArrayList<>();
        
        if (texts == null || texts.isEmpty()) {
            return allEmbeddings;
        }

        AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();
        int batchSize = config.getBatchSize();
        
        // 分批处理
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            List<float[]> batchResult = doEmbedRequest(batch);
            allEmbeddings.addAll(batchResult);
            
            // 批次间添加小延迟，避免频繁请求
            if (end < texts.size()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return allEmbeddings;
    }

    /**
     * 执行Embedding API请求
     */
    private List<float[]> doEmbedRequest(List<String> texts) {
        AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();
        List<float[]> embeddings = new ArrayList<>();
        
        int retries = 0;
        while (retries < config.getMaxRetries()) {
            try {
                // 构建请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", config.getModel());
                requestBody.put("input", texts);
                // 可选：指定维度
                // requestBody.put("dimensions", config.getDimension());
                
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                
                Request request = new Request.Builder()
                        .url(config.getApiUrl())
                        .addHeader("Authorization", "Bearer " + config.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "无响应体";
                        log.error("Embedding API请求失败: code={}, body={}", response.code(), errorBody);
                        retries++;
                        continue;
                    }

                    String responseBody = response.body().string();
                    JsonNode root = objectMapper.readTree(responseBody);
                    JsonNode dataArray = root.get("data");
                    
                    if (dataArray != null && dataArray.isArray()) {
                        for (JsonNode item : dataArray) {
                            JsonNode embeddingNode = item.get("embedding");
                            if (embeddingNode != null && embeddingNode.isArray()) {
                                float[] embedding = new float[embeddingNode.size()];
                                for (int j = 0; j < embeddingNode.size(); j++) {
                                    embedding[j] = (float) embeddingNode.get(j).asDouble();
                                }
                                embeddings.add(embedding);
                            }
                        }
                    }
                    
                    log.debug("Embedding成功: 处理{}条文本, 返回{}个向量", texts.size(), embeddings.size());
                    return embeddings;
                }
                
            } catch (IOException e) {
                log.error("Embedding API请求异常: {}", e.getMessage());
                retries++;
                
                if (retries < config.getMaxRetries()) {
                    try {
                        Thread.sleep(1000 * retries); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        log.error("Embedding API请求失败，已达最大重试次数: {}", config.getMaxRetries());
        // 返回空向量列表（或零向量）
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new float[getDimension()]);
        }
        return embeddings;
    }

    @Override
    public int getDimension() {
        return aiConfig.getEmbedding().getDimension();
    }

    @Override
    public boolean isAvailable() {
        AiConfig.EmbeddingConfig config = aiConfig.getEmbedding();
        
        if (config.getApiKey() == null || config.getApiKey().isEmpty() 
                || config.getApiKey().startsWith("your_")) {
            log.warn("Embedding服务未配置API密钥");
            return false;
        }
        
        // 尝试简单的测试请求
        try {
            float[] result = embed("测试");
            return result != null && result.length > 0;
        } catch (Exception e) {
            log.warn("Embedding服务可用性检测失败: {}", e.getMessage());
            return false;
        }
    }
}
