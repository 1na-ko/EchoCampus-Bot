package com.echocampus.bot.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.mapper.OperationLogMapper;
import com.echocampus.bot.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务实现类
 * 提供操作日志的异步记录和查询功能
 * 采用异步处理确保不影响主业务流程
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 异步保存操作日志
     * 使用独立线程池执行，确保主业务流程不受影响
     * 采用try-catch包装，即使日志保存失败也不会影响业务
     *
     * @param operationLog 操作日志实体
     */
    @Override
    @Async("taskExecutor")
    public void saveAsync(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
            log.debug("操作日志保存成功: userId={}, operationType={}, resourceType={}",
                    operationLog.getUserId(),
                    operationLog.getOperationType(),
                    operationLog.getResourceType());
        } catch (Exception e) {
            // 日志保存失败不应影响主业务，仅记录错误日志
            log.error("操作日志保存失败: userId={}, operationType={}, error={}",
                    operationLog.getUserId(),
                    operationLog.getOperationType(),
                    e.getMessage(), e);
        }
    }

    /**
     * 同步保存操作日志
     * 适用于需要立即确认日志已保存的场景
     *
     * @param operationLog 操作日志实体
     * @return 保存后的操作日志（含ID）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationLog save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
        log.debug("操作日志同步保存成功: id={}, userId={}, operationType={}",
                operationLog.getId(),
                operationLog.getUserId(),
                operationLog.getOperationType());
        return operationLog;
    }

    /**
     * 分页查询操作日志
     * 支持多条件组合查询，便于审计和数据分析
     *
     * @param page          当前页码（从1开始）
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
    @Override
    public IPage<OperationLog> queryPage(
            Integer page,
            Integer size,
            Long userId,
            String operationType,
            String resourceType,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String ipAddress) {
        
        // 参数校验和默认值处理
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100; // 限制最大每页数量，防止性能问题
        }

        Page<OperationLog> pageParam = new Page<>(page, size);
        
        return operationLogMapper.selectPageByConditions(
                pageParam,
                userId,
                operationType,
                resourceType,
                status,
                startTime,
                endTime,
                ipAddress
        );
    }

    /**
     * 根据ID查询操作日志详情
     *
     * @param id 日志ID
     * @return 操作日志详情，不存在返回null
     */
    @Override
    public OperationLog getById(Long id) {
        if (id == null) {
            return null;
        }
        return operationLogMapper.selectById(id);
    }

    /**
     * 查询指定用户的最近操作日志
     *
     * @param userId 用户ID
     * @param limit  限制数量，默认10条
     * @return 操作日志列表
     */
    @Override
    public List<OperationLog> getRecentByUserId(Long userId, Integer limit) {
        if (userId == null) {
            return List.of();
        }
        if (limit == null || limit < 1) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100;
        }
        return operationLogMapper.selectRecentByUserId(userId, limit);
    }

    /**
     * 统计指定时间范围内的操作数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 操作数量
     */
    @Override
    public Long countByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0L;
        }
        return operationLogMapper.countByTimeRange(startTime, endTime);
    }

    /**
     * 获取指定用户最后登录记录
     *
     * @param userId 用户ID
     * @return 最后登录的操作日志，不存在返回null
     */
    @Override
    public OperationLog getLastLoginByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return operationLogMapper.selectLastLoginByUserId(userId);
    }

    /**
     * 清理指定时间之前的历史日志
     * 用于日志归档和存储空间管理
     *
     * @param beforeTime 时间点
     * @return 删除的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanBeforeTime(LocalDateTime beforeTime) {
        if (beforeTime == null) {
            return 0;
        }
        int count = operationLogMapper.deleteBeforeTime(beforeTime);
        log.info("清理历史操作日志完成: beforeTime={}, deletedCount={}", beforeTime, count);
        return count;
    }
}
