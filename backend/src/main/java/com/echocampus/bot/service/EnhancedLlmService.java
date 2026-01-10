package com.echocampus.bot.service;

import com.echocampus.bot.entity.Message;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;
import java.util.function.Consumer;

/**
 * 增强的LLM服务接口 - 支持工具调用
 */
public interface EnhancedLlmService {
    
    /**
     * 聊天（支持工具调用）
     * 
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param historyMessages 历史消息
     * @param tools 可用的工具规范列表
     * @param toolExecutor 工具执行器
     * @return AI回复
     */
    String chatWithTools(String systemPrompt, 
                        String userPrompt,
                        List<Message> historyMessages,
                        List<ToolSpecification> tools,
                        ToolExecutor toolExecutor);
    
    /**
     * 流式聊天（支持工具调用）
     * 
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @param historyMessages 历史消息
     * @param tools 可用的工具规范列表
     * @param toolExecutor 工具执行器
     * @param contentConsumer 内容消费者
     * @return 完整的AI回复
     */
    String chatWithToolsStream(String systemPrompt,
                              String userPrompt,
                              List<Message> historyMessages,
                              List<ToolSpecification> tools,
                              ToolExecutor toolExecutor,
                              Consumer<String> contentConsumer);
    
    /**
     * 工具执行器接口
     */
    @FunctionalInterface
    interface ToolExecutor {
        /**
         * 执行工具调用
         * 
         * @param toolName 工具名称
         * @param arguments 工具参数（JSON格式）
         * @return 工具执行结果
         */
        String execute(String toolName, String arguments);
    }
}
