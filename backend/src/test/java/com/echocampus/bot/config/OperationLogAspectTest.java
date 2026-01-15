package com.echocampus.bot.config;

import com.echocampus.bot.annotation.OpLog;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 单元测试
 * 测试操作日志切面的各项功能
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OperationLogAspect - 操作日志切面测试")
class OperationLogAspectTest {

    @Mock
    private OperationLogService operationLogService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OperationLogAspect operationLogAspect;

    @BeforeEach
    void setUp() {
        // 清理 RequestContextHolder
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 测试用的带 @OpLog 注解的方法
     */
    @OpLog(
            operationType = OperationLog.OperationType.LOGIN,
            resourceType = OperationLog.ResourceType.USER,
            description = "用户登录"
    )
    public String testMethod(String username, String password) {
        return "success";
    }

    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.USER,
            description = "查询用户",
            saveRequestParams = false,
            saveResponseResult = false
    )
    public String testMethodNoParams() {
        return "success";
    }

    private Method getTestMethod(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        return this.getClass().getMethod(methodName, paramTypes);
    }

    @Nested
    @DisplayName("环绕通知测试")
    class AroundAdviceTests {

        @Test
        @DisplayName("成功执行应该记录成功状态的日志")
        void shouldLogSuccessWhenMethodSucceeds() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);
            OpLog opLog = method.getAnnotation(OpLog.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            doReturn("success").when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            // 设置 RequestContextHolder
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(100L).when(request).getAttribute("userId");
            doReturn("192.168.1.1").when(request).getHeader("X-Forwarded-For");
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // Act
            Object result = operationLogAspect.around(joinPoint);

            // Assert
            assertThat(result).isEqualTo("success");

            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getStatus()).isEqualTo(OperationLog.Status.SUCCESS);
            assertThat(capturedLog.getOperationType()).isEqualTo(OperationLog.OperationType.LOGIN);
            assertThat(capturedLog.getResourceType()).isEqualTo(OperationLog.ResourceType.USER);
            assertThat(capturedLog.getUserId()).isEqualTo(100L);
            assertThat(capturedLog.getExecutionTime()).isNotNull();
        }

        @Test
        @DisplayName("方法抛出异常应该记录失败状态的日志并重新抛出异常")
        void shouldLogFailureWhenMethodThrows() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            doThrow(new RuntimeException("Test error")).when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(100L).when(request).getAttribute("userId");
            doReturn(null).when(request).getHeader("X-Forwarded-For");
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // Act & Assert
            assertThatThrownBy(() -> operationLogAspect.around(joinPoint))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Test error");

            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getStatus()).isEqualTo(OperationLog.Status.FAILED);
            assertThat(capturedLog.getErrorMessage()).isEqualTo("Test error");
        }

        @Test
        @DisplayName("saveRequestParams为false时不应该记录请求参数")
        void shouldNotLogRequestParamsWhenDisabled() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethodNoParams");

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{}).when(signature).getParameterNames();
            doReturn(new Object[]{}).when(joinPoint).getArgs();
            doReturn("success").when(joinPoint).proceed();

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(null).when(request).getAttribute("userId");
            doReturn(null).when(request).getHeader("X-Forwarded-For");
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn(null).when(request).getHeader("User-Agent");
            doReturn("GET").when(request).getMethod();
            doReturn("/v1/user/profile").when(request).getRequestURI();

            // Act
            operationLogAspect.around(joinPoint);

            // Assert
            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getRequestParams()).isNull();
        }
    }

    @Nested
    @DisplayName("IP地址获取测试")
    class IpAddressTests {

        @Test
        @DisplayName("应该从X-Forwarded-For头获取IP")
        void shouldGetIpFromXForwardedFor() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            doReturn("success").when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(null).when(request).getAttribute("userId");
            doReturn("10.0.0.1, 192.168.1.1").when(request).getHeader("X-Forwarded-For");
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // Act
            operationLogAspect.around(joinPoint);

            // Assert
            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getIpAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("X-Forwarded-For为空时应该从RemoteAddr获取IP")
        void shouldFallbackToRemoteAddr() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            doReturn("success").when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(null).when(request).getAttribute("userId");
            doReturn(null).when(request).getHeader("X-Forwarded-For");
            doReturn(null).when(request).getHeader("X-Real-IP");
            doReturn(null).when(request).getHeader("Proxy-Client-IP");
            doReturn(null).when(request).getHeader("WL-Proxy-Client-IP");
            doReturn(null).when(request).getHeader("HTTP_CLIENT_IP");
            doReturn(null).when(request).getHeader("HTTP_X_FORWARDED_FOR");
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // Act
            operationLogAspect.around(joinPoint);

            // Assert
            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getIpAddress()).isEqualTo("127.0.0.1");
        }
    }

    @Nested
    @DisplayName("执行时间记录测试")
    class ExecutionTimeTests {

        @Test
        @DisplayName("应该记录方法执行时间")
        void shouldRecordExecutionTime() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            
            // 模拟耗时操作
            doAnswer(invocation -> {
                Thread.sleep(50);
                return "success";
            }).when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(null).when(request).getAttribute("userId");
            doReturn(null).when(request).getHeader("X-Forwarded-For");
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // Act
            operationLogAspect.around(joinPoint);

            // Assert
            ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
            verify(operationLogService).saveAsync(captor.capture());

            OperationLog capturedLog = captor.getValue();
            assertThat(capturedLog.getExecutionTime()).isGreaterThanOrEqualTo(50L);
        }
    }

    @Nested
    @DisplayName("异常安全测试")
    class ExceptionSafetyTests {

        @Test
        @DisplayName("日志保存失败不应该影响方法执行结果")
        void shouldNotAffectResultWhenLogSaveFails() throws Throwable {
            // Arrange
            Method method = getTestMethod("testMethod", String.class, String.class);

            doReturn(signature).when(joinPoint).getSignature();
            doReturn(method).when(signature).getMethod();
            doReturn(new String[]{"username", "password"}).when(signature).getParameterNames();
            doReturn(new Object[]{"testuser", "password123"}).when(joinPoint).getArgs();
            doReturn("success").when(joinPoint).proceed();
            doReturn("{}").when(objectMapper).writeValueAsString(any());

            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            doReturn(request).when(attributes).getRequest();
            RequestContextHolder.setRequestAttributes(attributes);

            doReturn(null).when(request).getAttribute("userId");
            doReturn(null).when(request).getHeader("X-Forwarded-For");
            doReturn("127.0.0.1").when(request).getRemoteAddr();
            doReturn("Mozilla/5.0").when(request).getHeader("User-Agent");
            doReturn("POST").when(request).getMethod();
            doReturn("/v1/auth/login").when(request).getRequestURI();

            // 模拟日志保存失败
            doThrow(new RuntimeException("Log save failed")).when(operationLogService).saveAsync(any());

            // Act
            Object result = operationLogAspect.around(joinPoint);

            // Assert - 方法执行结果不受影响
            assertThat(result).isEqualTo("success");
        }
    }
}
