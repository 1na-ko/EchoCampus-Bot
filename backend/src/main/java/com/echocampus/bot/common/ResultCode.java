package com.echocampus.bot.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    VALIDATION_ERROR(422, "参数校验失败"),

    // 服务端错误 5xx
    ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // 业务错误 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    USER_DISABLED(1004, "用户已禁用"),
    
    // 对话相关 2xxx
    CONVERSATION_NOT_FOUND(2001, "会话不存在"),
    MESSAGE_SEND_FAILED(2002, "消息发送失败"),
    
    // 知识库相关 3xxx
    DOC_NOT_FOUND(3001, "文档不存在"),
    DOC_UPLOAD_FAILED(3002, "文档上传失败"),
    DOC_PARSE_FAILED(3003, "文档解析失败"),
    DOC_ALREADY_EXISTS(3004, "文档已存在"),
    UNSUPPORTED_FILE_TYPE(3005, "不支持的文件类型"),
    FILE_TOO_LARGE(3006, "文件大小超过限制"),
    
    // AI服务相关 4xxx
    EMBEDDING_FAILED(4001, "向量化处理失败"),
    LLM_CALL_FAILED(4002, "大模型调用失败"),
    MILVUS_ERROR(4003, "向量数据库错误"),
    AI_SERVICE_TIMEOUT(4004, "AI服务响应超时"),
    
    // 系统配置相关 5xxx
    CONFIG_NOT_FOUND(5001, "配置不存在"),
    CONFIG_UPDATE_FAILED(5002, "配置更新失败");

    /**
     * 响应码
     */
    private final Integer code;

    /**
     * 响应消息
     */
    private final String message;
}
