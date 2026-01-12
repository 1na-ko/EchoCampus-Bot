package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.AiServiceConfig;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.service.EnhancedLlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
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
 * 增强的LLM服务实现 - 支持工具调用（基于DeepSeek API）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedLlmServiceImpl implements EnhancedLlmService {

    private final AiServiceConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile OkHttpClient httpClient;

    /**
     * 获取HTTP客户端（延迟初始化，线程安全）
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    int timeout = aiConfig.getLlm().getTimeout();
                    httpClient = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(timeout, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return httpClient;
    }

    @Override
    public String chatWithTools(String systemPrompt, String userPrompt, 
                               List<Message> historyMessages,
                               List<ToolSpecification> tools, 
                               ToolExecutor toolExecutor) {
        
        AiServiceConfig.LlmConfig config = aiConfig.getLlm();
        int maxIterations = 5; // 最大工具调用迭代次数
        
        try {
            // 构建消息列表
            List<Map<String, Object>> messages = buildMessages(systemPrompt, userPrompt, historyMessages);
            
            // 迭代处理工具调用
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                // 构建请求
                Map<String, Object> requestBody = buildRequestBody(config, messages, tools, false);
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                
                log.debug("LLM请求 (iteration {}): messages={}, tools={}", 
                         iteration, messages.size(), tools != null ? tools.size() : 0);
                
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
                    JsonNode choices = root.get("choices");
                    
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode firstChoice = choices.get(0);
                        JsonNode message = firstChoice.get("message");
                        
                        if (message != null) {
                            // 添加助手消息到历史
                            Map<String, Object> assistantMessage = objectMapper.convertValue(message, Map.class);
                            messages.add(assistantMessage);
                            
                            // 检查是否有工具调用
                            JsonNode toolCalls = message.get("tool_calls");
                            if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
                                log.info("AI请求调用 {} 个工具", toolCalls.size());
                                
                                // 执行所有工具调用
                                for (JsonNode toolCall : toolCalls) {
                                    String toolCallId = toolCall.get("id").asText();
                                    JsonNode function = toolCall.get("function");
                                    String toolName = function.get("name").asText();
                                    String arguments = function.get("arguments").asText();
                                    
                                    log.info("执行工具: name={}, args={}", toolName, arguments);
                                    
                                    // 执行工具
                                    String toolResult = toolExecutor.execute(toolName, arguments);
                                    
                                    // 添加工具结果到消息列表
                                    Map<String, Object> toolMessage = new HashMap<>();
                                    toolMessage.put("role", "tool");
                                    toolMessage.put("tool_call_id", toolCallId);
                                    toolMessage.put("content", toolResult);
                                    messages.add(toolMessage);
                                    
                                    log.info("工具执行完成: result length={}", toolResult.length());
                                }
                                
                                // 继续下一轮迭代，让AI基于工具结果生成回答
                                continue;
                            }
                            
                            // 没有工具调用，返回最终回答
                            String content = message.get("content").asText();
                            log.debug("LLM响应成功 (iteration {}): length={}", iteration, content.length());
                            return content;
                        }
                    }
                    
                    log.error("LLM响应格式异常: {}", responseBody);
                    return "抱歉，AI响应解析失败，请稍后再试。";
                }
            }
            
            log.warn("达到最大工具调用迭代次数: {}", maxIterations);
            return "抱歉，处理您的问题时遇到了复杂情况，请稍后再试。";
            
        } catch (IOException e) {
            log.error("LLM服务异常: {}", e.getMessage(), e);
            return "抱歉，AI服务出现异常：" + e.getMessage();
        } catch (Exception e) {
            log.error("处理LLM请求时发生未知错误: {}", e.getMessage(), e);
            return "抱歉，处理请求时发生错误，请稍后再试。";
        }
    }

    @Override
    public String chatWithToolsStream(String systemPrompt, String userPrompt,
                                     List<Message> historyMessages,
                                     List<ToolSpecification> tools,
                                     ToolExecutor toolExecutor,
                                     Consumer<String> contentConsumer) {
        
        AiServiceConfig.LlmConfig config = aiConfig.getLlm();
        int maxIterations = 5;
        StringBuilder fullResponse = new StringBuilder();
        
        try {
            // 构建消息列表
            List<Map<String, Object>> messages = buildMessages(systemPrompt, userPrompt, historyMessages);
            
            // 迭代处理工具调用
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                // 构建请求
                Map<String, Object> requestBody = buildRequestBody(config, messages, tools, true);
                String jsonBody = objectMapper.writeValueAsString(requestBody);
                
                log.debug("LLM流式请求 (iteration {}): messages={}, tools={}", 
                         iteration, messages.size(), tools != null ? tools.size() : 0);
                
                Request request = new Request.Builder()
                        .url(config.getApiUrl())
                        .addHeader("Authorization", "Bearer " + config.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "无响应体";
                        log.error("LLM API流式请求失败: code={}, body={}", response.code(), errorBody);
                        String errorMsg = "抱歉，AI服务暂时不可用，请稍后再试。";
                        contentConsumer.accept(errorMsg);
                        return errorMsg;
                    }

                    // 处理流式响应
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        String errorMsg = "抱歉，AI服务响应为空。";
                        contentConsumer.accept(errorMsg);
                        return errorMsg;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8));
                    
                    StringBuilder currentContent = new StringBuilder();
                    List<Map<String, Object>> currentToolCalls = new ArrayList<>();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty() || !line.startsWith("data: ")) {
                            continue;
                        }
                        
                        String data = line.substring(6); // 移除 "data: " 前缀
                        
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            JsonNode choices = chunk.get("choices");
                            
                            if (choices != null && choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                
                                if (delta != null) {
                                    // 处理内容
                                    JsonNode content = delta.get("content");
                                    if (content != null && !content.isNull()) {
                                        String contentChunk = content.asText();
                                        currentContent.append(contentChunk);
                                        fullResponse.append(contentChunk);
                                        contentConsumer.accept(contentChunk);
                                    }
                                    
                                    // 处理工具调用
                                    JsonNode toolCalls = delta.get("tool_calls");
                                    if (toolCalls != null && toolCalls.isArray()) {
                                        for (JsonNode toolCall : toolCalls) {
                                            int index = toolCall.get("index").asInt();
                                            
                                            // 确保列表足够大
                                            while (currentToolCalls.size() <= index) {
                                                currentToolCalls.add(new HashMap<>());
                                            }
                                            
                                            Map<String, Object> tc = currentToolCalls.get(index);
                                            
                                            if (toolCall.has("id")) {
                                                tc.put("id", toolCall.get("id").asText());
                                            }
                                            if (toolCall.has("type")) {
                                                tc.put("type", toolCall.get("type").asText());
                                            }
                                            
                                            JsonNode function = toolCall.get("function");
                                            if (function != null) {
                                                Map<String, Object> func = (Map<String, Object>) tc.computeIfAbsent("function", k -> new HashMap<>());
                                                
                                                if (function.has("name")) {
                                                    func.put("name", function.get("name").asText());
                                                }
                                                if (function.has("arguments")) {
                                                    String args = (String) func.getOrDefault("arguments", "");
                                                    args += function.get("arguments").asText();
                                                    func.put("arguments", args);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("解析流式响应块失败: {}", e.getMessage());
                        }
                    }
                    
                    // 检查是否有工具调用
                    if (!currentToolCalls.isEmpty()) {
                        log.info("AI请求调用 {} 个工具 (流式)", currentToolCalls.size());
                        
                        // 构建助手消息
                        Map<String, Object> assistantMessage = new HashMap<>();
                        assistantMessage.put("role", "assistant");
                        assistantMessage.put("content", currentContent.toString());
                        assistantMessage.put("tool_calls", currentToolCalls);
                        messages.add(assistantMessage);
                        
                        // 执行所有工具调用
                        for (Map<String, Object> toolCall : currentToolCalls) {
                            String toolCallId = (String) toolCall.get("id");
                            Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                            String toolName = (String) function.get("name");
                            String arguments = (String) function.get("arguments");
                            
                            log.info("执行工具 (流式): name={}, args={}", toolName, arguments);
                            
                            // 执行工具
                            String toolResult = toolExecutor.execute(toolName, arguments);
                            
                            // 添加工具结果到消息列表
                            Map<String, Object> toolMessage = new HashMap<>();
                            toolMessage.put("role", "tool");
                            toolMessage.put("tool_call_id", toolCallId);
                            toolMessage.put("content", toolResult);
                            messages.add(toolMessage);
                            
                            log.info("工具执行完成 (流式): result length={}", toolResult.length());
                        }
                        
                        // 继续下一轮迭代
                        continue;
                    }
                    
                    // 没有工具调用，返回最终回答
                    log.debug("LLM流式响应成功 (iteration {}): length={}", iteration, fullResponse.length());
                    return fullResponse.toString();
                }
            }
            
            log.warn("达到最大工具调用迭代次数 (流式): {}", maxIterations);
            String errorMsg = "抱歉，处理您的问题时遇到了复杂情况，请稍后再试。";
            if (fullResponse.length() == 0) {
                contentConsumer.accept(errorMsg);
            }
            return fullResponse.length() > 0 ? fullResponse.toString() : errorMsg;
            
        } catch (IOException e) {
            log.error("LLM流式服务异常: {}", e.getMessage(), e);
            String errorMsg = "抱歉，AI服务出现异常：" + e.getMessage();
            contentConsumer.accept(errorMsg);
            return errorMsg;
        } catch (Exception e) {
            log.error("处理LLM流式请求时发生未知错误: {}", e.getMessage(), e);
            String errorMsg = "抱歉，处理请求时发生错误，请稍后再试。";
            contentConsumer.accept(errorMsg);
            return errorMsg;
        }
    }

    /**
     * 构建消息列表
     */
    private List<Map<String, Object>> buildMessages(String systemPrompt, String userPrompt, List<Message> historyMessages) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
        }
        
        // 添加历史消息
        if (historyMessages != null && !historyMessages.isEmpty()) {
            for (Message msg : historyMessages) {
                Map<String, Object> historyMsg = new HashMap<>();
                historyMsg.put("role", "USER".equals(msg.getSenderType()) ? "user" : "assistant");
                historyMsg.put("content", msg.getContent());
                messages.add(historyMsg);
            }
        }
        
        // 添加当前用户消息
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        
        return messages;
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(AiServiceConfig.LlmConfig config,
                                                 List<Map<String, Object>> messages,
                                                 List<ToolSpecification> tools,
                                                 boolean stream) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("stream", stream);
        
        // 添加工具定义
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolDefs = new ArrayList<>();
            for (ToolSpecification tool : tools) {
                Map<String, Object> toolDef = new HashMap<>();
                toolDef.put("type", "function");
                
                Map<String, Object> function = new HashMap<>();
                function.put("name", tool.name());
                function.put("description", tool.description());
                
                // 构建参数schema
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("type", "object");
                
                Map<String, Object> properties = new HashMap<>();
                List<String> required = new ArrayList<>();
                
                if (tool.parameters() != null && tool.parameters().properties() != null) {
                    tool.parameters().properties().forEach((name, param) -> {
                        Map<String, Object> propDef = new HashMap<>();
                        propDef.put("type", (String) param.get("type"));
                        propDef.put("description", (String) param.get("description"));
                        properties.put(name, propDef);
                        
                        // 检查是否必需（简化处理，假设都是必需的）
                        required.add(name);
                    });
                }
                
                parameters.put("properties", properties);
                if (!required.isEmpty()) {
                    parameters.put("required", required);
                }
                
                function.put("parameters", parameters);
                toolDef.put("function", function);
                toolDefs.add(toolDef);
            }
            
            requestBody.put("tools", toolDefs);
            requestBody.put("tool_choice", "auto"); // 让AI自主决定是否调用工具
        }
        
        return requestBody;
    }
}
