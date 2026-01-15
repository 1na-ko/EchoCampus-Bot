package com.echocampus.bot.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.mapper.OperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OperationLogServiceImpl 单元测试
 * 测试操作日志服务的各项功能
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationLogServiceImpl - 操作日志服务测试")
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private OperationLogServiceImpl operationLogService;

    private OperationLog testLog;

    @BeforeEach
    void setUp() {
        testLog = createTestOperationLog();
    }

    /**
     * 创建测试用的操作日志对象
     */
    private OperationLog createTestOperationLog() {
        return OperationLog.builder()
                .id(1L)
                .userId(100L)
                .operationType(OperationLog.OperationType.LOGIN)
                .resourceType(OperationLog.ResourceType.USER)
                .operationDesc("用户登录")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .requestMethod("POST")
                .requestUrl("/v1/auth/login")
                .status(OperationLog.Status.SUCCESS)
                .executionTime(150L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("同步保存日志测试")
    class SaveTests {

        @Test
        @DisplayName("保存操作日志应该成功并返回带ID的日志对象")
        void shouldSaveOperationLogSuccessfully() {
            // Arrange
            doAnswer(invocation -> {
                OperationLog log = invocation.getArgument(0);
                log.setId(1L);
                return 1;
            }).when(operationLogMapper).insert(any(OperationLog.class));

            OperationLog newLog = OperationLog.builder()
                    .userId(100L)
                    .operationType(OperationLog.OperationType.LOGIN)
                    .resourceType(OperationLog.ResourceType.USER)
                    .status(OperationLog.Status.SUCCESS)
                    .build();

            // Act
            OperationLog result = operationLogService.save(newLog);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(operationLogMapper).insert(any(OperationLog.class));
        }

        @Test
        @DisplayName("保存日志时应该传递所有字段")
        void shouldPassAllFieldsWhenSaving() {
            // Arrange
            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            doReturn(1).when(operationLogMapper).insert(captor.capture());

            // Act
            operationLogService.save(testLog);

            // Assert
            OperationLog captured = captor.getValue();
            assertThat(captured.getUserId()).isEqualTo(100L);
            assertThat(captured.getOperationType()).isEqualTo(OperationLog.OperationType.LOGIN);
            assertThat(captured.getResourceType()).isEqualTo(OperationLog.ResourceType.USER);
            assertThat(captured.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(captured.getStatus()).isEqualTo(OperationLog.Status.SUCCESS);
        }
    }

    @Nested
    @DisplayName("分页查询测试")
    class QueryPageTests {

        @Test
        @DisplayName("分页查询应该返回正确的结果")
        void shouldReturnPagedResults() {
            // Arrange
            Page<OperationLog> page = new Page<>(1, 10);
            page.setRecords(Arrays.asList(testLog));
            page.setTotal(1);

            doReturn(page).when(operationLogMapper).selectPageByConditions(
                    any(Page.class), anyLong(), anyString(), anyString(),
                    anyString(), any(), any(), anyString());

            // Act
            IPage<OperationLog> result = operationLogService.queryPage(
                    1, 10, 100L, OperationLog.OperationType.LOGIN,
                    OperationLog.ResourceType.USER, OperationLog.Status.SUCCESS,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now(), null);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("页码小于1时应该使用默认值1")
        void shouldUseDefaultPageWhenPageIsInvalid() {
            // Arrange
            Page<OperationLog> page = new Page<>(1, 20);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            doReturn(page).when(operationLogMapper).selectPageByConditions(
                    any(Page.class), any(), any(), any(), any(), any(), any(), any());

            // Act
            IPage<OperationLog> result = operationLogService.queryPage(
                    -1, 20, null, null, null, null, null, null, null);

            // Assert
            assertThat(result).isNotNull();
            verify(operationLogMapper).selectPageByConditions(
                    argThat(p -> p.getCurrent() == 1), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("每页大小超过100时应该限制为100")
        void shouldLimitSizeTo100() {
            // Arrange
            Page<OperationLog> page = new Page<>(1, 100);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);

            doReturn(page).when(operationLogMapper).selectPageByConditions(
                    any(Page.class), any(), any(), any(), any(), any(), any(), any());

            // Act
            IPage<OperationLog> result = operationLogService.queryPage(
                    1, 200, null, null, null, null, null, null, null);

            // Assert
            assertThat(result).isNotNull();
            verify(operationLogMapper).selectPageByConditions(
                    argThat(p -> p.getSize() == 100), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("按ID查询测试")
    class GetByIdTests {

        @Test
        @DisplayName("根据ID查询应该返回对应的日志")
        void shouldReturnLogById() {
            // Arrange
            doReturn(testLog).when(operationLogMapper).selectById(1L);

            // Act
            OperationLog result = operationLogService.getById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("ID为null时应该返回null")
        void shouldReturnNullWhenIdIsNull() {
            // Act
            OperationLog result = operationLogService.getById(null);

            // Assert
            assertThat(result).isNull();
            verify(operationLogMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("ID不存在时应该返回null")
        void shouldReturnNullWhenNotFound() {
            // Arrange
            doReturn(null).when(operationLogMapper).selectById(999L);

            // Act
            OperationLog result = operationLogService.getById(999L);

            // Assert
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("查询用户最近操作日志测试")
    class GetRecentByUserIdTests {

        @Test
        @DisplayName("查询用户最近日志应该返回正确的列表")
        void shouldReturnRecentLogs() {
            // Arrange
            List<OperationLog> logs = Arrays.asList(testLog);
            doReturn(logs).when(operationLogMapper).selectRecentByUserId(100L, 10);

            // Act
            List<OperationLog> result = operationLogService.getRecentByUserId(100L, 10);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("用户ID为null时应该返回空列表")
        void shouldReturnEmptyListWhenUserIdIsNull() {
            // Act
            List<OperationLog> result = operationLogService.getRecentByUserId(null, 10);

            // Assert
            assertThat(result).isEmpty();
            verify(operationLogMapper, never()).selectRecentByUserId(any(), any());
        }

        @Test
        @DisplayName("limit为null时应该使用默认值10")
        void shouldUseDefaultLimitWhenNull() {
            // Arrange
            List<OperationLog> logs = Arrays.asList(testLog);
            doReturn(logs).when(operationLogMapper).selectRecentByUserId(100L, 10);

            // Act
            List<OperationLog> result = operationLogService.getRecentByUserId(100L, null);

            // Assert
            assertThat(result).hasSize(1);
            verify(operationLogMapper).selectRecentByUserId(100L, 10);
        }

        @Test
        @DisplayName("limit超过100时应该限制为100")
        void shouldLimitTo100() {
            // Arrange
            doReturn(Collections.emptyList()).when(operationLogMapper).selectRecentByUserId(100L, 100);

            // Act
            operationLogService.getRecentByUserId(100L, 500);

            // Assert
            verify(operationLogMapper).selectRecentByUserId(100L, 100);
        }
    }

    @Nested
    @DisplayName("统计操作数量测试")
    class CountByTimeRangeTests {

        @Test
        @DisplayName("统计指定时间范围内的操作数量")
        void shouldCountOperationsInTimeRange() {
            // Arrange
            LocalDateTime startTime = LocalDateTime.now().minusDays(7);
            LocalDateTime endTime = LocalDateTime.now();
            doReturn(100L).when(operationLogMapper).countByTimeRange(startTime, endTime);

            // Act
            Long count = operationLogService.countByTimeRange(startTime, endTime);

            // Assert
            assertThat(count).isEqualTo(100L);
        }

        @Test
        @DisplayName("时间参数为null时应该返回0")
        void shouldReturnZeroWhenTimeIsNull() {
            // Act
            Long count = operationLogService.countByTimeRange(null, null);

            // Assert
            assertThat(count).isEqualTo(0L);
            verify(operationLogMapper, never()).countByTimeRange(any(), any());
        }
    }

    @Nested
    @DisplayName("查询最后登录记录测试")
    class GetLastLoginTests {

        @Test
        @DisplayName("查询用户最后登录记录")
        void shouldReturnLastLogin() {
            // Arrange
            doReturn(testLog).when(operationLogMapper).selectLastLoginByUserId(100L);

            // Act
            OperationLog result = operationLogService.getLastLoginByUserId(100L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getOperationType()).isEqualTo(OperationLog.OperationType.LOGIN);
        }

        @Test
        @DisplayName("用户ID为null时应该返回null")
        void shouldReturnNullWhenUserIdIsNull() {
            // Act
            OperationLog result = operationLogService.getLastLoginByUserId(null);

            // Assert
            assertThat(result).isNull();
            verify(operationLogMapper, never()).selectLastLoginByUserId(any());
        }
    }

    @Nested
    @DisplayName("清理历史日志测试")
    class CleanBeforeTimeTests {

        @Test
        @DisplayName("清理指定时间之前的日志")
        void shouldCleanLogsBeforeTime() {
            // Arrange
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(30);
            doReturn(50).when(operationLogMapper).deleteBeforeTime(beforeTime);

            // Act
            int count = operationLogService.cleanBeforeTime(beforeTime);

            // Assert
            assertThat(count).isEqualTo(50);
            verify(operationLogMapper).deleteBeforeTime(beforeTime);
        }

        @Test
        @DisplayName("时间参数为null时应该返回0")
        void shouldReturnZeroWhenTimeIsNull() {
            // Act
            int count = operationLogService.cleanBeforeTime(null);

            // Assert
            assertThat(count).isEqualTo(0);
            verify(operationLogMapper, never()).deleteBeforeTime(any());
        }
    }
}
