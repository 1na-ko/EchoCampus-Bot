package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.AiConfig;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * DeepSeek LLM服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OkHttpClient httpClient;

    /**
     * 获取HTTP客户端（延迟初始化）
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            int timeout = aiConfig.getLlm().getTimeout();
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(timeout, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    /** RAG系统提示词模板 */
    private static final String RAG_SYSTEM_PROMPT = """
            你是EchoCampus智能校园问答助手，专门回答与校园相关的问题。
            
            请根据以下知识库内容回答用户的问题：
            
            【知识库内容】
            %s
            
            【回答要求】
            1. 只根据提供的知识库内容回答，不要编造信息
            2. 如果知识库中没有相关内容，请明确告知用户
            3. 回答要简洁、准确、有条理
            4. 如有必要，可以使用编号或分点说明
            5. 使用友好的语气与用户交流
            """;

    @Override
    public String chat(String prompt) {
        return chat(null, prompt);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.add(ChatMessage.user(userPrompt));
        
        return chat(messages);
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        AiConfig.LlmConfig config = aiConfig.getLlm();
        
        try {
            // 构建请求体
            List<Map<String, String>> messageList = new ArrayList<>();
            for (ChatMessage msg : messages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", msg.role());
                msgMap.put("content", msg.content());
                messageList.add(msgMap);
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", messageList);
            requestBody.put("max_tokens", config.getMaxTokens());
            requestBody.put("temperature", config.getTemperature());
            requestBody.put("stream", false);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            log.debug("LLM请求: model={}, messages={}", config.getModel(), messages.size());
            
            Request request = new Request.Builder()
                    .url(config.getApiUrl())
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("LLM API请求失败: code={}, body={}", response.code(), errorBody);
                    return "抱歉，AI服务暂时不可用，请稍后再试。";
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                
                // 解析响应
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode firstChoice = choices.get(0);
                    JsonNode message = firstChoice.get("message");
                    if (message != null) {
                        String content = message.get("content").asText();
                        log.debug("LLM响应成功: 长度={}", content.length());
                        return content;
                    }
                }
                
                log.error("LLM响应格式异常: {}", responseBody);
                return "抱歉，AI响应解析失败，请稍后再试。";
            }
            
        } catch (IOException e) {
            log.error("LLM API请求异常: {}", e.getMessage(), e);
            return "抱歉，AI服务请求失败: " + e.getMessage();
        }
    }

    @Override
    public String ragAnswer(String question, String context) {
        if (context == null || context.trim().isEmpty()) {
            // 没有检索到相关内容
            return chat("你是EchoCampus智能校园问答助手。", 
                    "用户问题：" + question + "\n\n请告知用户，当前知识库中没有找到与该问题相关的内容。");
        }
        
        // 使用RAG模板
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);
        return chat(systemPrompt, question);
    }

    @Override
    public String ragAnswer(String question, String context, List<Message> historyMessages) {
        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        
        // 1. 添加系统提示词（包含知识库上下文）
        if (context != null && !context.trim().isEmpty()) {
            String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);
            messages.add(ChatMessage.system(systemPrompt));
        } else {
            messages.add(ChatMessage.system("你是EchoCampus智能校园问答助手。当前知识库中没有找到与该问题相关的内容，请告知用户。"));
        }
        
        // 2. 添加历史消息（排除当前问题）
        if (historyMessages != null && !historyMessages.isEmpty()) {
            // 只取最近10轮对话（20条消息）
            historyMessages.stream()
                    .limit(20)
                    .forEach(msg -> {
                        if ("USER".equals(msg.getSenderType())) {
                            messages.add(ChatMessage.user(msg.getContent()));
                        } else if ("BOT".equals(msg.getSenderType())) {
                            messages.add(ChatMessage.assistant(msg.getContent()));
                        }
                    });
        }
        
        // 3. 添加当前问题
        messages.add(ChatMessage.user(question));
        
        // 4. 调用LLM
        return chat(messages);
    }

    @Override
    public boolean isAvailable() {
        AiConfig.LlmConfig config = aiConfig.getLlm();
        
        if (config.getApiKey() == null || config.getApiKey().isEmpty() 
                || config.getApiKey().startsWith("your_")) {
            log.warn("LLM服务未配置API密钥");
            return false;
        }
        
        // 简单测试
        try {
            String response = chat("你好");
            return response != null && !response.contains("抱歉");
        } catch (Exception e) {
            log.warn("LLM服务可用性检测失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void ragAnswerStream(String question, String context, List<Message> historyMessages, Consumer<String> chunkConsumer) {
        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        
        // 1. 添加系统提示词（包含知识库上下文）
        if (context != null && !context.trim().isEmpty()) {
            String systemPrompt = String.format(RAG_SYSTEM_PROMPT, context);
            messages.add(ChatMessage.system(systemPrompt));
        } else {
            messages.add(ChatMessage.system("你是EchoCampus智能校园问答助手。当前知识库中没有找到与该问题相关的内容，请告知用户。"));
        }
        
        // 2. 添加历史消息（排除当前问题）
        if (historyMessages != null && !historyMessages.isEmpty()) {
            historyMessages.stream()
                    .limit(20)
                    .forEach(msg -> {
                        if ("USER".equals(msg.getSenderType())) {
                            messages.add(ChatMessage.user(msg.getContent()));
                        } else if ("BOT".equals(msg.getSenderType())) {
                            messages.add(ChatMessage.assistant(msg.getContent()));
                        }
                    });
        }
        
        // 3. 添加当前问题
        messages.add(ChatMessage.user(question));
        
        // 4. 流式调用LLM
        chatStream(messages, chunkConsumer);
    }

    /**
     * 流式聊天调用
     */
    private void chatStream(List<ChatMessage> messages, Consumer<String> chunkConsumer) {
        AiConfig.LlmConfig config = aiConfig.getLlm();
        
        try {
            // 构建请求体
            List<Map<String, String>> messageList = new ArrayList<>();
            for (ChatMessage msg : messages) {
                Map<String, String> msgMap = new HashMap<>();
                msgMap.put("role", msg.role());
                msgMap.put("content", msg.content());
                messageList.add(msgMap);
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", messageList);
            requestBody.put("max_tokens", config.getMaxTokens());
            requestBody.put("temperature", config.getTemperature());
            requestBody.put("stream", true); // 启用流式
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            log.debug("LLM流式请求: model={}, messages={}", config.getModel(), messages.size());
            
            Request request = new Request.Builder()
                    .url(config.getApiUrl())
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("LLM API流式请求失败: code={}, body={}", response.code(), errorBody);
                    chunkConsumer.accept("抱歉，AI服务暂时不可用，请稍后再试。");
                    return;
                }

                // 解析SSE流
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                
                                try {
                                    JsonNode root = objectMapper.readTree(data);
                                    JsonNode choices = root.get("choices");
                                    if (choices != null && choices.isArray() && choices.size() > 0) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.get("content").asText();
                                            if (content != null && !content.isEmpty()) {
                                                chunkConsumer.accept(content);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("解析流式响应数据失败: {}", data);
                                }
                            }
                        }
                    }
                }
                log.debug("LLM流式响应完成");
            }
            
        } catch (IOException e) {
            log.error("LLM API流式请求异常: {}", e.getMessage(), e);
            chunkConsumer.accept("抱歉，AI服务请求失败: " + e.getMessage());
        }
    }
}
