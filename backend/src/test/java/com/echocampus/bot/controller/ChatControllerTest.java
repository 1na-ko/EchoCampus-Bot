package com.echocampus.bot.controller;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.config.RateLimitConfig;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.filter.JwtAuthenticationFilter;
import com.echocampus.bot.filter.XssFilter;
import com.echocampus.bot.service.ChatService;
import com.echocampus.bot.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ChatController 控制器测试
 * P2 优先级 - API契约验证
 * 
 * 注意：由于 @WebMvcTest 与 MyBatis-Plus 自动配置冲突，这些测试暂时被禁用。
 * 建议改用 @SpringBootTest 或手动配置 ApplicationContext。
 */
@WebMvcTest(value = ChatController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, XssFilter.class})
})
@AutoConfigureMockMvc(addFilters = false) // 禁用安全过滤器
@TestPropertySource(properties = "spring.autoconfigure.exclude=org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@DisplayName("ChatController - 聊天控制器测试")
@Disabled("@WebMvcTest 与 MyBatis-Plus 自动配置存在冲突，需要改用集成测试")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private Executor sseExecutor;

    @MockBean
    private RateLimitConfig.RateLimiter rateLimiter;

    @MockBean
    private JwtUtil jwtUtil;

    private ChatRequest chatRequest;
    private ChatResponse chatResponse;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        chatRequest = createChatRequest();
        chatResponse = createChatResponse();
        testConversation = createTestConversation();
    }

    private ChatRequest createChatRequest() {
        ChatRequest request = new ChatRequest();
        request.setConversationId(1L);
        request.setMessage("测试消息");
        request.setEnableContext(true);
        request.setContextRounds(5);
        return request;
    }

    private ChatResponse createChatResponse() {
        return ChatResponse.builder()
            .messageId(1L)
            .conversationId(1L)
            .answer("AI回复内容")
            .sources(Collections.emptyList())
            .usage(ChatResponse.TokenUsage.builder()
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .build())
            .responseTimeMs(500L)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private Conversation createTestConversation() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setUserId(1L);
        conversation.setTitle("测试对话");
        conversation.setMessageCount(0);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversation;
    }

    private Message createTestMessage(Long id, String content, String senderType) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(1L);
        message.setContent(content);
        message.setSenderType(senderType);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    @Nested
    @DisplayName("发送消息接口测试 - POST /v1/chat/message")
    class SendMessageTests {

        @Test
        @DisplayName("应该成功发送消息并返回AI回复")
        void shouldSendMessageSuccessfully() throws Exception {
            // Arrange
            when(chatService.sendMessage(anyLong(), any(ChatRequest.class))).thenReturn(chatResponse);

            // Act & Assert
            mockMvc.perform(post("/v1/chat/message")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("AI回复内容"))
                .andExpect(jsonPath("$.data.conversationId").value(1))
                .andExpect(jsonPath("$.data.messageId").value(1));
        }

        @Test
        @DisplayName("消息内容为空应该返回400")
        void shouldReturn400ForEmptyMessage() throws Exception {
            // Arrange
            chatRequest.setMessage("");

            // Act & Assert
            mockMvc.perform(post("/v1/chat/message")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("消息内容过长应该返回400")
        void shouldReturn400ForTooLongMessage() throws Exception {
            // Arrange
            chatRequest.setMessage("a".repeat(2001)); // 超过2000字符

            // Act & Assert
            mockMvc.perform(post("/v1/chat/message")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("会话不存在应该返回业务错误")
        void shouldReturnErrorWhenConversationNotFound() throws Exception {
            // Arrange
            when(chatService.sendMessage(anyLong(), any(ChatRequest.class)))
                .thenThrow(new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));

            // Act & Assert
            mockMvc.perform(post("/v1/chat/message")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.CONVERSATION_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("没有会话ID应该创建新对话")
        void shouldCreateNewConversationWhenNoId() throws Exception {
            // Arrange
            chatRequest.setConversationId(null);
            ChatResponse responseWithNewConversation = ChatResponse.builder()
                .messageId(1L)
                .conversationId(2L) // 新创建的会话ID
                .answer("AI回复")
                .sources(Collections.emptyList())
                .build();
            
            when(chatService.sendMessage(anyLong(), any(ChatRequest.class)))
                .thenReturn(responseWithNewConversation);

            // Act & Assert
            mockMvc.perform(post("/v1/chat/message")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(chatRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(2));
        }
    }

    @Nested
    @DisplayName("获取会话列表接口测试 - GET /v1/chat/conversations")
    class GetConversationsTests {

        @Test
        @DisplayName("应该成功获取会话列表")
        void shouldGetConversationsSuccessfully() throws Exception {
            // Arrange
            List<Conversation> conversations = List.of(testConversation);
            when(chatService.getConversations(anyLong(), anyInt(), anyInt())).thenReturn(conversations);

            // Act & Assert
            mockMvc.perform(get("/v1/chat/conversations")
                    .requestAttr("userId", 1L)
                    .param("page", "1")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("测试对话"));
        }

        @Test
        @DisplayName("使用默认分页参数")
        void shouldUseDefaultPaginationParams() throws Exception {
            // Arrange
            when(chatService.getConversations(anyLong(), eq(1), eq(10))).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/v1/chat/conversations")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(chatService).getConversations(anyLong(), eq(1), eq(10));
        }
    }

    @Nested
    @DisplayName("获取会话消息接口测试 - GET /v1/chat/conversations/{conversationId}/messages")
    class GetMessagesTests {

        @Test
        @DisplayName("应该成功获取会话消息")
        void shouldGetMessagesSuccessfully() throws Exception {
            // Arrange
            List<Message> messages = List.of(
                createTestMessage(1L, "用户消息", "USER"),
                createTestMessage(2L, "AI回复", "BOT")
            );
            when(chatService.getMessages(1L)).thenReturn(messages);

            // Act & Assert
            mockMvc.perform(get("/v1/chat/conversations/1/messages")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].senderType").value("USER"))
                .andExpect(jsonPath("$.data[1].senderType").value("BOT"));
        }

        @Test
        @DisplayName("空会话应该返回空数组")
        void shouldReturnEmptyArrayForEmptyConversation() throws Exception {
            // Arrange
            when(chatService.getMessages(1L)).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/v1/chat/conversations/1/messages")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Nested
    @DisplayName("创建会话接口测试 - POST /v1/chat/conversations")
    class CreateConversationTests {

        @Test
        @DisplayName("应该成功创建新会话")
        void shouldCreateConversationSuccessfully() throws Exception {
            // Arrange
            when(chatService.createConversation(anyLong(), anyString())).thenReturn(testConversation);

            // Act & Assert
            mockMvc.perform(post("/v1/chat/conversations")
                    .requestAttr("userId", 1L)
                    .param("title", "新对话"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试对话"));
        }

        @Test
        @DisplayName("使用默认标题创建会话")
        void shouldUseDefaultTitle() throws Exception {
            // Arrange
            when(chatService.createConversation(anyLong(), eq("新对话"))).thenReturn(testConversation);

            // Act & Assert
            mockMvc.perform(post("/v1/chat/conversations")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(chatService).createConversation(anyLong(), eq("新对话"));
        }
    }

    @Nested
    @DisplayName("删除会话接口测试 - DELETE /v1/chat/conversations/{conversationId}")
    class DeleteConversationTests {

        @Test
        @DisplayName("应该成功删除会话")
        void shouldDeleteConversationSuccessfully() throws Exception {
            // Arrange
            doNothing().when(chatService).deleteConversation(1L);

            // Act & Assert
            mockMvc.perform(delete("/v1/chat/conversations/1")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(chatService).deleteConversation(1L);
        }

        @Test
        @DisplayName("删除不存在的会话应该返回业务错误")
        void shouldReturnErrorWhenConversationNotFound() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.CONVERSATION_NOT_FOUND))
                .when(chatService).deleteConversation(999L);

            // Act & Assert
            mockMvc.perform(delete("/v1/chat/conversations/999")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.CONVERSATION_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("更新会话标题接口测试 - PUT /v1/chat/conversations/{conversationId}")
    class UpdateConversationTitleTests {

        @Test
        @DisplayName("应该成功更新会话标题")
        void shouldUpdateConversationTitleSuccessfully() throws Exception {
            // Arrange
            doNothing().when(chatService).updateConversationTitle(1L, "新标题");

            // Act & Assert
            mockMvc.perform(put("/v1/chat/conversations/1")
                    .requestAttr("userId", 1L)
                    .param("title", "新标题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(chatService).updateConversationTitle(1L, "新标题");
        }

        @Test
        @DisplayName("更新不存在的会话标题应该返回业务错误")
        void shouldReturnErrorWhenUpdatingNonExistentConversation() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.CONVERSATION_NOT_FOUND))
                .when(chatService).updateConversationTitle(eq(999L), anyString());

            // Act & Assert
            mockMvc.perform(put("/v1/chat/conversations/999")
                    .requestAttr("userId", 1L)
                    .param("title", "新标题"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.CONVERSATION_NOT_FOUND.getCode()));
        }
    }
}
