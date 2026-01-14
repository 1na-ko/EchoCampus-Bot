package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.ChatRequest;
import com.echocampus.bot.dto.response.ChatResponse;
import com.echocampus.bot.dto.response.StreamChatResponse;
import com.echocampus.bot.entity.Conversation;
import com.echocampus.bot.entity.Message;
import com.echocampus.bot.mapper.ConversationMapper;
import com.echocampus.bot.mapper.MessageMapper;
import com.echocampus.bot.service.EnhancedRagService;
import com.echocampus.bot.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatServiceImpl 单元测试
 * P1 优先级 - 对话核心业务
 * 
 * 注意：由于 MyBatis-Plus BaseMapper 与 Mockito 的兼容性问题，这些测试暂时被禁用。
 * 建议改用集成测试（@SpringBootTest）或使用内存数据库进行测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatServiceImpl - 聊天服务测试")
@Disabled("MyBatis-Plus BaseMapper 与 Mockito 存在兼容性问题，需要改用集成测试")
class ChatServiceImplTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private RagService ragService;

    @Mock
    private EnhancedRagService enhancedRagService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private Conversation testConversation;
    private ChatRequest testChatRequest;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        // 默认启用增强模式
        ReflectionTestUtils.setField(chatService, "enhancedMode", true);
        
        testConversation = createTestConversation();
        testChatRequest = createTestChatRequest();
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

    private ChatRequest createTestChatRequest() {
        ChatRequest request = new ChatRequest();
        request.setConversationId(1L);
        request.setMessage("这是测试消息");
        request.setEnableContext(true);
        request.setContextRounds(5);
        return request;
    }

    private Message createTestMessage(Long id, Long conversationId, String content, String senderType) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setContent(content);
        message.setSenderType(senderType);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    @Nested
    @DisplayName("发送消息测试 - sendMessage")
    class SendMessageTests {

        @Test
        @DisplayName("正常发送消息应该返回AI回复")
        void shouldSendMessageAndReturnResponse() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse(
                "这是AI的回复",
                Collections.emptyList(),
                100L
            );
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            ChatResponse response = chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAnswer()).isEqualTo("这是AI的回复");
            assertThat(response.getConversationId()).isEqualTo(1L);
            assertThat(response.getMessageId()).isNotNull();
            assertThat(response.getResponseTimeMs()).isGreaterThanOrEqualTo(0);

            // Verify
            verify(messageMapper, times(2)).insert(any(Message.class)); // 用户消息 + AI回复
        }

        @Test
        @DisplayName("会话不存在应该抛出异常")
        void shouldThrowExceptionWhenConversationNotFound() {
            // Arrange
            doReturn(null).when(conversationMapper).selectById(999L);
            testChatRequest.setConversationId(999L);

            // Act & Assert
            assertThatThrownBy(() -> chatService.sendMessage(testUserId, testChatRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.CONVERSATION_NOT_FOUND.getCode());
                });

            verify(messageMapper, never()).insert(any(Message.class));
        }

        @Test
        @DisplayName("没有会话ID应该创建新会话")
        void shouldCreateNewConversationWhenNoConversationId() {
            // Arrange
            testChatRequest.setConversationId(null);
            
            Conversation newConversation = createTestConversation();
            newConversation.setId(2L);
            
            // 模拟insert操作设置ID
            doAnswer(invocation -> {
                Conversation conv = invocation.getArgument(0);
                conv.setId(2L);
                return 1;
            }).when(conversationMapper).insert(any(Conversation.class));

            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(2L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            ChatResponse response = chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            assertThat(response).isNotNull();
            verify(conversationMapper).insert(any(Conversation.class));
        }

        @Test
        @DisplayName("应该正确保存用户消息")
        void shouldSaveUserMessage() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageMapper, times(2)).insert(messageCaptor.capture());
            
            List<Message> savedMessages = messageCaptor.getAllValues();
            Message userMessage = savedMessages.get(0);
            assertThat(userMessage.getSenderType()).isEqualTo("USER");
            assertThat(userMessage.getContent()).isEqualTo("这是测试消息");
            assertThat(userMessage.getConversationId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("应该正确保存AI回复消息")
        void shouldSaveAiResponseMessage() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("AI回复内容", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(messageMapper, times(2)).insert(messageCaptor.capture());
            
            Message botMessage = messageCaptor.getAllValues().get(1);
            assertThat(botMessage.getSenderType()).isEqualTo("BOT");
            assertThat(botMessage.getContent()).isEqualTo("AI回复内容");
        }

        @Test
        @DisplayName("应该正确处理知识库来源")
        void shouldHandleSourceDocs() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            List<RagService.SourceInfo> sources = List.of(
                new RagService.SourceInfo(1L, "文档1", 1L, "内容片段1", 0.95f),
                new RagService.SourceInfo(2L, "文档2", 2L, "内容片段2", 0.85f)
            );
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", sources, 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            ChatResponse response = chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            assertThat(response.getSources()).hasSize(2);
            assertThat(response.getSources().get(0).getDocId()).isEqualTo(1L);
            assertThat(response.getSources().get(0).getTitle()).isEqualTo("文档1");
            assertThat(response.getSources().get(0).getSimilarity()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("传统模式应该调用ragService")
        void shouldUseRagServiceInTraditionalMode() {
            // Arrange
            ReflectionTestUtils.setField(chatService, "enhancedMode", false);
            
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(ragService).answer(anyString(), anyList(), anyLong(), anyLong());

            // Act
            chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            verify(ragService).answer(anyString(), anyList(), anyLong(), anyLong());
            verify(enhancedRagService, never()).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("应该获取历史消息作为上下文")
        void shouldRetrieveHistoryMessagesForContext() {
            // Arrange
            List<Message> historyMessages = new ArrayList<>();
            historyMessages.add(createTestMessage(1L, 1L, "历史问题", "USER"));
            historyMessages.add(createTestMessage(2L, 1L, "历史回复", "BOT"));
            
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(historyMessages).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            chatService.sendMessage(testUserId, testChatRequest);

            // Assert - 验证传递了历史消息
            ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
            verify(enhancedRagService).answerWithAutoRetrieval(anyString(), historyCaptor.capture(), anyLong(), anyLong());
            
            // 历史消息应该被排序和限制
            assertThat(historyCaptor.getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("会话管理测试")
    class ConversationManagementTests {

        @Test
        @DisplayName("应该成功获取会话列表")
        void shouldGetConversations() {
            // Arrange
            List<Conversation> conversations = List.of(testConversation);
            doReturn(conversations).when(conversationMapper).selectRecentByUserId(1L, 10);

            // Act
            List<Conversation> result = chatService.getConversations(1L, 1, 10);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("应该成功创建新会话")
        void shouldCreateConversation() {
            // Arrange
            doAnswer(invocation -> {
                Conversation conv = invocation.getArgument(0);
                conv.setId(10L);
                return 1;
            }).when(conversationMapper).insert(any(Conversation.class));

            // Act
            Conversation result = chatService.createConversation(1L, "新对话标题");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("新对话标题");
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getMessageCount()).isEqualTo(0);
            
            verify(conversationMapper).insert(any(Conversation.class));
        }

        @Test
        @DisplayName("应该成功删除会话（软删除）")
        void shouldDeleteConversation() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);

            // Act
            chatService.deleteConversation(1L);

            // Assert
            ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).updateById(conversationCaptor.capture());
            assertThat(conversationCaptor.getValue().getStatus()).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("删除不存在的会话应该抛出异常")
        void shouldThrowExceptionWhenDeletingNonExistentConversation() {
            // Arrange
            doReturn(null).when(conversationMapper).selectById(999L);

            // Act & Assert
            assertThatThrownBy(() -> chatService.deleteConversation(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.CONVERSATION_NOT_FOUND.getCode());
                });
        }

        @Test
        @DisplayName("应该成功更新会话标题")
        void shouldUpdateConversationTitle() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);

            // Act
            chatService.updateConversationTitle(1L, "新标题");

            // Assert
            ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).updateById(conversationCaptor.capture());
            assertThat(conversationCaptor.getValue().getTitle()).isEqualTo("新标题");
        }

        @Test
        @DisplayName("更新不存在会话的标题应该抛出异常")
        void shouldThrowExceptionWhenUpdatingNonExistentConversation() {
            // Arrange
            doReturn(null).when(conversationMapper).selectById(999L);

            // Act & Assert
            assertThatThrownBy(() -> chatService.updateConversationTitle(999L, "新标题"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.CONVERSATION_NOT_FOUND.getCode());
                });
        }
    }

    @Nested
    @DisplayName("消息管理测试")
    class MessageManagementTests {

        @Test
        @DisplayName("应该成功获取会话消息")
        void shouldGetMessages() {
            // Arrange
            List<Message> messages = List.of(
                createTestMessage(1L, 1L, "用户消息", "USER"),
                createTestMessage(2L, 1L, "AI回复", "BOT")
            );
            doReturn(messages).when(messageMapper).selectByConversationId(1L);

            // Act
            List<Message> result = chatService.getMessages(1L);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSenderType()).isEqualTo("USER");
            assertThat(result.get(1).getSenderType()).isEqualTo("BOT");
        }

        @Test
        @DisplayName("空会话应该返回空列表")
        void shouldReturnEmptyListForEmptyConversation() {
            // Arrange
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);

            // Act
            List<Message> result = chatService.getMessages(1L);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @ParameterizedTest
        @DisplayName("不同分页参数应该正确传递")
        @ValueSource(ints = {1, 5, 10, 20, 50})
        void shouldHandleDifferentPageSizes(int size) {
            // Arrange
            doReturn(new ArrayList<>()).when(conversationMapper).selectRecentByUserId(anyLong(), eq(size));

            // Act
            chatService.getConversations(1L, 1, size);

            // Assert
            verify(conversationMapper).selectRecentByUserId(1L, size);
        }

        @Test
        @DisplayName("应该记录响应时间")
        void shouldRecordResponseTime() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            ChatResponse response = chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            assertThat(response.getResponseTimeMs()).isGreaterThanOrEqualTo(0);
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Token使用统计应该返回默认值")
        void shouldReturnDefaultTokenUsage() {
            // Arrange
            doReturn(testConversation).when(conversationMapper).selectById(1L);
            doReturn(new ArrayList<>()).when(messageMapper).selectByConversationId(1L);
            
            RagService.RagResponse ragResponse = new RagService.RagResponse("回复", Collections.emptyList(), 100L);
            doReturn(ragResponse).when(enhancedRagService).answerWithAutoRetrieval(anyString(), anyList(), anyLong(), anyLong());

            // Act
            ChatResponse response = chatService.sendMessage(testUserId, testChatRequest);

            // Assert
            assertThat(response.getUsage()).isNotNull();
            assertThat(response.getUsage().getTotalTokens()).isEqualTo(0);
        }
    }
}
