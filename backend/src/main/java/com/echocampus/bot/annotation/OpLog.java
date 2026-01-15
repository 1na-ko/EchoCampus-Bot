package com.echocampus.bot.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的Controller方法
 * 通过AOP切面自动拦截并记录用户操作行为
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @OpLog(
 *     operationType = OperationType.CREATE,
 *     resourceType = ResourceType.DOC,
 *     description = "上传知识库文档"
 * )
 * @PostMapping("/upload")
 * public Result<KnowledgeDoc> uploadDocument(...) {
 *     // 业务逻辑
 * }
 * }
 * </pre>
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /**
     * 操作类型
     * 定义本次操作的类型，如：登录、注册、创建、更新、删除等
     *
     * @return 操作类型字符串
     * @see com.echocampus.bot.entity.OperationLog.OperationType
     */
    String operationType() default "";

    /**
     * 资源类型
     * 定义本次操作涉及的资源类型，如：用户、文档、配置等
     *
     * @return 资源类型字符串
     * @see com.echocampus.bot.entity.OperationLog.ResourceType
     */
    String resourceType() default "";

    /**
     * 操作描述
     * 对本次操作的详细说明
     * 支持SpEL表达式，可以动态获取方法参数值
     * 例如："上传文档：#{#fileName}"
     *
     * @return 操作描述字符串
     */
    String description() default "";

    /**
     * 是否记录请求参数
     * 默认为true，会记录请求的参数信息
     * 对于敏感接口（如密码修改），可设置为false
     *
     * @return 是否记录请求参数
     */
    boolean saveRequestParams() default true;

    /**
     * 是否记录响应结果
     * 默认为true，会记录响应的关键信息
     * 对于大数据量响应，可设置为false以节省存储空间
     *
     * @return 是否记录响应结果
     */
    boolean saveResponseResult() default true;

    /**
     * 需要脱敏的参数名
     * 指定需要进行脱敏处理的请求参数名称
     * 如：password、oldPassword、newPassword等
     *
     * @return 需要脱敏的参数名数组
     */
    String[] maskParams() default {"password", "oldPassword", "newPassword", "verificationCode"};
}
