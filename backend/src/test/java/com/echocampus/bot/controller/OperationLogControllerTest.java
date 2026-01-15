package com.echocampus.bot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.dto.request.OperationLogQueryRequest;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OperationLogController 单元测试
 * 测试操作日志控制器的各项功能
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationLogController - 操作日志控制器测试")
class OperationLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OperationLogService operationLogService;

    @InjectMocks
    private OperationLogController operationLogController;

    private ObjectMapper objectMapper;
    private OperationLog testLog;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(operationLogController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testLog = createTestOperationLog();
    }

    private OperationLog createTestOperationLog() {
        return OperationLog.builder()
                .id(1L)
                .userId(100L)
                .operationType(OperationLog.OperationType.LOGIN)
                .operationDesc("用户登录")
                .resourceType(OperationLog.ResourceType.USER)
                .ipAddress("192.168.1.1")
                .requestMethod("POST")
                .requestUrl("/v1/auth/login")
                .status(OperationLog.Status.SUCCESS)
                .executionTime(150L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("分页查询测试")
    class QueryPageTests {

        @Test
        @DisplayName("分页查询应该返回成功结果")
        void shouldReturnPagedResults() throws Exception {
            // Arrange
            Page<OperationLog> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testLog));
            page.setTotal(1);

            doReturn(page).when(operationLogService).queryPage(
                    anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());

            OperationLogQueryRequest request = OperationLogQueryRequest.builder()
                    .page(1)
                    .size(10)
                    .build();

            // Act & Assert
            mockMvc.perform(post("/v1/admin/operation-logs/page")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].id").value(1));
        }

        @Test
        @DisplayName("带条件的分页查询应该传递所有参数")
        void shouldPassAllQueryParams() throws Exception {
            // Arrange
            Page<OperationLog> page = new Page<>(1, 10);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            doReturn(page).when(operationLogService).queryPage(
                    eq(1), eq(10), eq(100L),
                    eq(OperationLog.OperationType.LOGIN),
                    eq(OperationLog.ResourceType.USER),
                    eq(OperationLog.Status.SUCCESS),
                    any(), any(), eq("192.168"));

            OperationLogQueryRequest request = OperationLogQueryRequest.builder()
                    .page(1)
                    .size(10)
                    .userId(100L)
                    .operationType(OperationLog.OperationType.LOGIN)
                    .resourceType(OperationLog.ResourceType.USER)
                    .status(OperationLog.Status.SUCCESS)
                    .ipAddress("192.168")
                    .build();

            // Act & Assert
            mockMvc.perform(post("/v1/admin/operation-logs/page")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(operationLogService).queryPage(
                    eq(1), eq(10), eq(100L),
                    eq(OperationLog.OperationType.LOGIN),
                    eq(OperationLog.ResourceType.USER),
                    eq(OperationLog.Status.SUCCESS),
                    any(), any(), eq("192.168"));
        }
    }

    @Nested
    @DisplayName("按ID查询测试")
    class GetByIdTests {

        @Test
        @DisplayName("根据ID查询应该返回日志详情")
        void shouldReturnLogById() throws Exception {
            // Arrange
            doReturn(testLog).when(operationLogService).getById(1L);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.userId").value(100));
        }

        @Test
        @DisplayName("查询不存在的ID应该返回错误")
        void shouldReturnErrorWhenNotFound() throws Exception {
            // Arrange
            doReturn(null).when(operationLogService).getById(999L);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("操作日志不存在"));
        }
    }

    @Nested
    @DisplayName("查询用户最近日志测试")
    class GetRecentByUserIdTests {

        @Test
        @DisplayName("查询用户最近日志应该返回列表")
        void shouldReturnRecentLogs() throws Exception {
            // Arrange
            List<OperationLog> logs = Arrays.asList(testLog);
            doReturn(logs).when(operationLogService).getRecentByUserId(100L, 10);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/user/100/recent")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }

        @Test
        @DisplayName("使用默认limit值应该生效")
        void shouldUseDefaultLimit() throws Exception {
            // Arrange
            List<OperationLog> logs = Arrays.asList(testLog);
            doReturn(logs).when(operationLogService).getRecentByUserId(100L, 10);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/user/100/recent"))
                    .andExpect(status().isOk());

            verify(operationLogService).getRecentByUserId(100L, 10);
        }
    }

    @Nested
    @DisplayName("查询最后登录记录测试")
    class GetLastLoginTests {

        @Test
        @DisplayName("查询用户最后登录记录应该返回成功")
        void shouldReturnLastLogin() throws Exception {
            // Arrange
            doReturn(testLog).when(operationLogService).getLastLoginByUserId(100L);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/user/100/last-login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.operationType").value("LOGIN"));
        }

        @Test
        @DisplayName("未找到登录记录应该返回错误")
        void shouldReturnErrorWhenNoLoginRecord() throws Exception {
            // Arrange
            doReturn(null).when(operationLogService).getLastLoginByUserId(100L);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/user/100/last-login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("未找到登录记录"));
        }
    }

    @Nested
    @DisplayName("查询我的日志测试")
    class GetMyLogsTests {

        @Test
        @DisplayName("查询当前用户日志应该返回列表")
        void shouldReturnMyLogs() throws Exception {
            // Arrange
            List<OperationLog> logs = Arrays.asList(testLog);
            doReturn(logs).when(operationLogService).getRecentByUserId(100L, 20);

            // Act & Assert
            mockMvc.perform(get("/v1/admin/operation-logs/my")
                            .requestAttr("userId", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
