package com.echocampus.bot.service;

import com.echocampus.bot.entity.Message;

import java.util.List;

/**
 * LLM大语言模型服务接口
 */
public interface LlmService {

    /**
     * 简单对话（无上下文）
     *
     * @param prompt 用户提问
     * @return AI回复
     */
    String chat(String prompt);

    /**
     * 带系统提示词的对话
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提问
     * @return AI回复
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 多轮对话
     *
     * @param messages 消息历史
     * @return AI回复
     */
    String chat(List<ChatMessage> messages);

    /**
     * RAG问答（带知识库上下文）
     *
     * @param question 用户问题
     * @param context 检索到的知识库上下文
     * @return AI回复
     */
    String ragAnswer(String question, String context);

    /**
     * RAG问答（带知识库上下文和历史消息）
     *
     * @param question 用户问题
     * @param context 检索到的知识库上下文
     * @param historyMessages 历史消息
     * @return AI回复
     */
    String ragAnswer(String question, String context, List<Message> historyMessages);

    /**
     * 检查服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 聊天消息
     */
    record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }
}
