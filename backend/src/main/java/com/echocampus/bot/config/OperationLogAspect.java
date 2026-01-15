package com.echocampus.bot.config;

import com.echocampus.bot.annotation.OpLog;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.service.OperationLogService;
import com.echocampus.bot.utils.DateTimeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志切面
 * 通过AOP方式拦截带有@OpLog注解的方法，自动记录用户操作日志
 * 采用异步方式处理日志记录，确保不影响主业务流程性能
 *
 * <p>功能特性：</p>
 * <ul>
 *   <li>自动捕获请求参数、响应结果、执行耗时等信息</li>
 *   <li>支持敏感参数脱敏（如密码、验证码等）</li>
 *   <li>异常捕获与处理，确保日志记录失败不影响业务</li>
 *   <li>支持从请求上下文获取用户ID和IP地址</li>
 * </ul>
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    /**
     * 请求参数和响应结果的最大长度
     * 超过此长度会被截断，防止数据库存储问题
     */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /**
     * 定义切入点：所有带有@OpLog注解的方法
     */
    @Pointcut("@annotation(com.echocampus.bot.annotation.OpLog)")
    public void operationLogPointcut() {
    }

    /**
     * 环绕通知：在方法执行前后记录操作日志
     *
     * @param joinPoint 连接点，包含方法执行的上下文信息
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中抛出的异常
     */
    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 获取方法签名和注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OpLog opLog = method.getAnnotation(OpLog.class);
        
        // 获取HTTP请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        // 构建操作日志对象
        OperationLog operationLog = buildOperationLog(joinPoint, opLog, request);
        
        Object result = null;
        Throwable exception = null;
        
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            
            // 记录成功状态
            operationLog.setStatus(OperationLog.Status.SUCCESS);
            
            // 记录响应结果（如果配置允许）
            if (opLog.saveResponseResult() && result != null) {
                operationLog.setResponseResult(truncateString(toJsonString(result)));
            }
            
            return result;
        } catch (Throwable e) {
            // 记录失败状态和错误信息
            exception = e;
            operationLog.setStatus(OperationLog.Status.FAILED);
            operationLog.setErrorMessage(truncateString(e.getMessage()));
            
            // 重新抛出异常，不影响原有的异常处理流程
            throw e;
        } finally {
            // 计算执行耗时
            long executionTime = System.currentTimeMillis() - startTime;
            operationLog.setExecutionTime(executionTime);
            
            // 异步保存操作日志
            saveOperationLogSafely(operationLog);
            
            // 打印日志便于调试
            if (exception != null) {
                log.debug("操作日志记录完成[失败]: method={}, executionTime={}ms, error={}",
                        method.getName(), executionTime, exception.getMessage());
            } else {
                log.debug("操作日志记录完成[成功]: method={}, executionTime={}ms",
                        method.getName(), executionTime);
            }
        }
    }

    /**
     * 构建操作日志对象
     *
     * @param joinPoint 连接点
     * @param opLog     操作日志注解
     * @param request   HTTP请求
     * @return 操作日志对象
     */
    private OperationLog buildOperationLog(ProceedingJoinPoint joinPoint, OpLog opLog, HttpServletRequest request) {
        OperationLog operationLog = new OperationLog();
        
        // 设置操作类型和资源类型
        operationLog.setOperationType(opLog.operationType());
        operationLog.setResourceType(opLog.resourceType());
        operationLog.setOperationDesc(opLog.description());
        
        // 设置操作时间
        operationLog.setCreatedAt(DateTimeUtil.now());
        
        if (request != null) {
            // 设置用户ID（从请求属性中获取，由JWT拦截器设置）
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr != null) {
                operationLog.setUserId((Long) userIdAttr);
            }
            
            // 设置IP地址
            operationLog.setIpAddress(getClientIp(request));
            
            // 设置User-Agent
            operationLog.setUserAgent(truncateString(request.getHeader("User-Agent")));
            
            // 设置请求方法和URL
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestUrl(request.getRequestURI());
        }
        
        // 设置请求参数（如果配置允许）
        if (opLog.saveRequestParams()) {
            String params = getMethodParams(joinPoint, opLog.maskParams());
            operationLog.setRequestParams(truncateString(params));
        }
        
        return operationLog;
    }

    /**
     * 获取方法参数并转换为JSON字符串
     * 对敏感参数进行脱敏处理
     *
     * @param joinPoint  连接点
     * @param maskParams 需要脱敏的参数名
     * @return 参数的JSON字符串
     */
    private String getMethodParams(ProceedingJoinPoint joinPoint, String[] maskParams) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] paramValues = joinPoint.getArgs();
            
            if (paramNames == null || paramNames.length == 0) {
                return "{}";
            }
            
            // 需要脱敏的参数名集合
            Set<String> maskParamSet = Arrays.stream(maskParams)
                    .collect(Collectors.toSet());
            
            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                String paramName = paramNames[i];
                Object paramValue = paramValues[i];
                
                // 跳过不适合序列化的参数类型
                if (shouldSkipParam(paramValue)) {
                    params.put(paramName, "[" + paramValue.getClass().getSimpleName() + "]");
                    continue;
                }
                
                // 对敏感参数进行脱敏
                if (maskParamSet.contains(paramName)) {
                    params.put(paramName, "******");
                } else {
                    params.put(paramName, paramValue);
                }
            }
            
            return toJsonString(params);
        } catch (Exception e) {
            log.warn("获取方法参数失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 判断是否应该跳过该参数（不进行序列化）
     *
     * @param paramValue 参数值
     * @return 是否跳过
     */
    private boolean shouldSkipParam(Object paramValue) {
        if (paramValue == null) {
            return false;
        }
        return paramValue instanceof HttpServletRequest
                || paramValue instanceof HttpServletResponse
                || paramValue instanceof MultipartFile
                || paramValue instanceof MultipartFile[];
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return obj.toString();
        }
    }

    /**
     * 截断字符串，防止超出数据库字段长度限制
     *
     * @param str 原字符串
     * @return 截断后的字符串
     */
    private String truncateString(String str) {
        if (str == null) {
            return null;
        }
        if (str.length() <= MAX_CONTENT_LENGTH) {
            return str;
        }
        return str.substring(0, MAX_CONTENT_LENGTH) + "...[truncated]";
    }

    /**
     * 获取客户端真实IP地址
     * 支持通过代理服务器获取真实IP
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }
        
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip;
        }
        
        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 判断IP地址是否有效
     *
     * @param ip IP地址
     * @return 是否有效
     */
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }

    /**
     * 安全地保存操作日志
     * 捕获所有异常，确保日志记录失败不影响主业务
     *
     * @param operationLog 操作日志
     */
    private void saveOperationLogSafely(OperationLog operationLog) {
        try {
            operationLogService.saveAsync(operationLog);
        } catch (Exception e) {
            // 日志保存失败不应影响主业务，仅记录错误日志
            log.error("保存操作日志失败: userId={}, operationType={}, error={}",
                    operationLog.getUserId(),
                    operationLog.getOperationType(),
                    e.getMessage());
        }
    }
}
