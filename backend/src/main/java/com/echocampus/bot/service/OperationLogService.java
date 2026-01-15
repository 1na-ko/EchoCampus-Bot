package com.echocampus.bot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.echocampus.bot.entity.OperationLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务接口
 * 提供操作日志的记录和查询功能
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
public interface OperationLogService {

    /**
     * 异步保存操作日志
     * 采用异步方式，确保不影响主业务流程性能
     *
     * @param operationLog 操作日志实体
     */
    void saveAsync(OperationLog operationLog);

    /**
     * 同步保存操作日志
     * 适用于需要立即确认日志已保存的场景
     *
     * @param operationLog 操作日志实体
     * @return 保存后的操作日志（含ID）
     */
    OperationLog save(OperationLog operationLog);

    /**
     * 分页查询操作日志
     * 支持多条件组合查询
     *
     * @param page          当前页码
     * @param size          每页大小
     * @param userId        用户ID（可选）
     * @param operationType 操作类型（可选）
     * @param resourceType  资源类型（可选）
     * @param status        操作状态（可选）
     * @param startTime     开始时间（可选）
     * @param endTime       结束时间（可选）
     * @param ipAddress     IP地址（可选，支持模糊匹配）
     * @return 分页查询结果
     */
    IPage<OperationLog> queryPage(
            Integer page,
            Integer size,
            Long userId,
            String operationType,
            String resourceType,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String ipAddress
    );

    /**
     * 根据ID查询操作日志详情
     *
     * @param id 日志ID
     * @return 操作日志详情
     */
    OperationLog getById(Long id);

    /**
     * 查询指定用户的最近操作日志
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 操作日志列表
     */
    List<OperationLog> getRecentByUserId(Long userId, Integer limit);

    /**
     * 统计指定时间范围内的操作数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 操作数量
     */
    Long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定用户最后登录记录
     *
     * @param userId 用户ID
     * @return 最后登录的操作日志
     */
    OperationLog getLastLoginByUserId(Long userId);

    /**
     * 清理指定时间之前的历史日志
     * 用于日志归档和存储空间管理
     *
     * @param beforeTime 时间点
     * @return 删除的记录数
     */
    int cleanBeforeTime(LocalDateTime beforeTime);
}
