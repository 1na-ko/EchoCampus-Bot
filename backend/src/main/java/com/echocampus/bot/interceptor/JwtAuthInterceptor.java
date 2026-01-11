package com.echocampus.bot.interceptor;

import com.echocampus.bot.annotation.RequireAuth;
import com.echocampus.bot.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        log.info("JwtAuthInterceptor: 请求路径 = {}", requestUri);

        if (!(handler instanceof HandlerMethod)) {
            log.info("JwtAuthInterceptor: 非HandlerMethod，跳过拦截");
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);

        if (requireAuth == null) {
            log.info("JwtAuthInterceptor: 无@RequireAuth注解，跳过拦截");
            return true;
        }

        log.info("JwtAuthInterceptor: 需要@RequireAuth认证");

        String token = extractToken(request);
        if (token == null) {
            log.warn("JwtAuthInterceptor: 未提供认证token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未提供认证token\",\"success\":false}");
            return false;
        }

        if (!jwtUtil.validateToken(token)) {
            log.warn("JwtAuthInterceptor: token无效或已过期");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\",\"success\":false}");
            return false;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        log.info("JwtAuthInterceptor: 认证成功 - userId={}, username={}, role={}", userId, username, role);

        request.setAttribute("userId", userId);
        request.setAttribute("username", username);
        request.setAttribute("role", role);

        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
