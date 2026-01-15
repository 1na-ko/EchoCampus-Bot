package com.echocampus.bot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 * 用于记录用户的所有操作行为，支持审计和数据分析
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("operation_logs")
public class OperationLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户ID
     * 可为空，表示匿名用户操作
     */
    private Long userId;

    /**
     * 操作类型
     * 如：LOGIN（登录）、LOGOUT（登出）、CREATE（创建）、UPDATE（更新）、DELETE（删除）、QUERY（查询）、UPLOAD（上传）等
     */
    private String operationType;

    /**
     * 操作描述
     * 详细说明本次操作的具体内容
     */
    private String operationDesc;

    /**
     * 资源类型
     * 如：USER（用户）、DOC（文档）、CHUNK（文档片段）、CONFIG（配置）、CONVERSATION（对话）等
     */
    private String resourceType;

    /**
     * 资源ID
     * 被操作资源的唯一标识
     */
    private Long resourceId;

    /**
     * 用户IP地址
     * 支持IPv4和IPv6格式
     */
    private String ipAddress;

    /**
     * 用户代理信息（浏览器标识）
     * 记录客户端浏览器和操作系统信息
     */
    private String userAgent;

    /**
     * 请求方法
     * HTTP请求方法：GET、POST、PUT、DELETE等
     */
    private String requestMethod;

    /**
     * 请求URL
     * 完整的请求路径
     */
    private String requestUrl;

    /**
     * 请求参数
     * JSON格式存储，包含请求的查询参数和请求体
     */
    private String requestParams;

    /**
     * 响应结果
     * JSON格式存储，包含响应的关键信息
     */
    private String responseResult;

    /**
     * 操作状态
     * SUCCESS（成功）或 FAILED（失败）
     */
    private String status;

    /**
     * 错误信息
     * 当操作失败时，记录详细的错误信息
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     * 记录操作从开始到结束的时间
     */
    private Long executionTime;

    /**
     * 操作时间
     * 精确到毫秒的操作发生时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 操作状态枚举
     */
    public static class Status {
        /** 操作成功 */
        public static final String SUCCESS = "SUCCESS";
        /** 操作失败 */
        public static final String FAILED = "FAILED";
    }

    /**
     * 操作类型枚举
     */
    public static class OperationType {
        /** 用户登录 */
        public static final String LOGIN = "LOGIN";
        /** 用户登出 */
        public static final String LOGOUT = "LOGOUT";
        /** 用户注册 */
        public static final String REGISTER = "REGISTER";
        /** 创建操作 */
        public static final String CREATE = "CREATE";
        /** 更新操作 */
        public static final String UPDATE = "UPDATE";
        /** 删除操作 */
        public static final String DELETE = "DELETE";
        /** 查询操作 */
        public static final String QUERY = "QUERY";
        /** 上传操作 */
        public static final String UPLOAD = "UPLOAD";
        /** 下载操作 */
        public static final String DOWNLOAD = "DOWNLOAD";
        /** 发送验证码 */
        public static final String SEND_CODE = "SEND_CODE";
        /** 修改密码 */
        public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
        /** 聊天对话 */
        public static final String CHAT = "CHAT";
        /** 其他操作 */
        public static final String OTHER = "OTHER";
    }

    /**
     * 资源类型枚举
     */
    public static class ResourceType {
        /** 用户资源 */
        public static final String USER = "USER";
        /** 知识库文档 */
        public static final String DOC = "DOC";
        /** 文档片段 */
        public static final String CHUNK = "CHUNK";
        /** 系统配置 */
        public static final String CONFIG = "CONFIG";
        /** 对话会话 */
        public static final String CONVERSATION = "CONVERSATION";
        /** 消息 */
        public static final String MESSAGE = "MESSAGE";
        /** 知识库分类 */
        public static final String CATEGORY = "CATEGORY";
        /** 验证码 */
        public static final String VERIFICATION_CODE = "VERIFICATION_CODE";
        /** 系统 */
        public static final String SYSTEM = "SYSTEM";
    }
}
