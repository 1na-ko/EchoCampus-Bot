package com.echocampus.bot.interceptor;

import com.echocampus.bot.annotation.RequireRole;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Slf4j
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        if (requireRole == null) {
            return true;
        }

        String userRole = (String) request.getAttribute("role");
        if (userRole == null) {
            log.warn("用户未认证，无法访问需要权限的接口: {}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        boolean hasRole = Arrays.asList(requireRole.value()).contains(userRole);
        if (!hasRole) {
            log.warn("用户权限不足，用户角色={}, 需要角色={}, 请求URI={}", 
                userRole, Arrays.toString(requireRole.value()), request.getRequestURI());
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        log.debug("用户权限验证通过，用户角色={}, 请求URI={}", userRole, request.getRequestURI());
        return true;
    }
}
