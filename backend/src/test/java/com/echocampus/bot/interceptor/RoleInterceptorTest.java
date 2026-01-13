package com.echocampus.bot.interceptor;

import com.echocampus.bot.annotation.RequireRole;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @InjectMocks
    private RoleInterceptor roleInterceptor;

    @BeforeEach
    void setUp() {
        lenient().when(handlerMethod.getMethod()).thenReturn(mock(Method.class));
    }

    @Test
    void testPreHandle_NoRequireRoleAnnotation_ShouldReturnTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

        boolean result = roleInterceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        verify(request, never()).getAttribute(anyString());
    }

    @Test
    void testPreHandle_WithValidRole_ShouldReturnTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(createRequireRoleAnnotation("ADMIN"));
        when(request.getAttribute("role")).thenReturn("ADMIN");

        boolean result = roleInterceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    @Test
    void testPreHandle_WithInvalidRole_ShouldThrowForbiddenException() throws Exception {
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(createRequireRoleAnnotation("ADMIN"));
        when(request.getAttribute("role")).thenReturn("USER");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            roleInterceptor.preHandle(request, response, handlerMethod);
        });

        assertEquals(ResultCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void testPreHandle_WithoutRole_ShouldThrowUnauthorizedException() throws Exception {
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(createRequireRoleAnnotation("ADMIN"));
        when(request.getAttribute("role")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            roleInterceptor.preHandle(request, response, handlerMethod);
        });

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), exception.getCode());
    }

    @Test
    void testPreHandle_WithMultipleRoles_ShouldReturnTrue() throws Exception {
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(createRequireRoleAnnotation("ADMIN", "USER"));
        when(request.getAttribute("role")).thenReturn("USER");

        boolean result = roleInterceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    private RequireRole createRequireRoleAnnotation(String... roles) {
        return new RequireRole() {
            @Override
            public String[] value() {
                return roles;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequireRole.class;
            }
        };
    }
}
