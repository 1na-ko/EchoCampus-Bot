package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志Mapper接口
 * 提供操作日志的数据库访问能力
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /**
     * 分页查询操作日志
     * 支持多条件组合查询
     *
     * @param page          分页参数
     * @param userId        用户ID（可选）
     * @param operationType 操作类型（可选）
     * @param resourceType  资源类型（可选）
     * @param status        操作状态（可选）
     * @param startTime     开始时间（可选）
     * @param endTime       结束时间（可选）
     * @param ipAddress     IP地址（可选，支持模糊匹配）
     * @return 分页查询结果
     */
    @Select("<script>"
            + "SELECT * FROM operation_logs "
            + "<where>"
            + "  <if test='userId != null'> AND user_id = #{userId} </if>"
            + "  <if test='operationType != null and operationType != \"\"'> AND operation_type = #{operationType} </if>"
            + "  <if test='resourceType != null and resourceType != \"\"'> AND resource_type = #{resourceType} </if>"
            + "  <if test='status != null and status != \"\"'> AND status = #{status} </if>"
            + "  <if test='startTime != null'> AND created_at &gt;= #{startTime} </if>"
            + "  <if test='endTime != null'> AND created_at &lt;= #{endTime} </if>"
            + "  <if test='ipAddress != null and ipAddress != \"\"'> AND ip_address LIKE CONCAT('%', #{ipAddress}, '%') </if>"
            + "</where>"
            + "ORDER BY created_at DESC"
            + "</script>")
    IPage<OperationLog> selectPageByConditions(
            Page<OperationLog> page,
            @Param("userId") Long userId,
            @Param("operationType") String operationType,
            @Param("resourceType") String resourceType,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("ipAddress") String ipAddress
    );

    /**
     * 查询指定用户的最近操作日志
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 操作日志列表
     */
    @Select("SELECT * FROM operation_logs WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<OperationLog> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 统计指定时间范围内的操作数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 操作数量
     */
    @Select("SELECT COUNT(*) FROM operation_logs WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Long countByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 按操作类型统计数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计结果列表，包含操作类型和数量
     */
    @Select("SELECT operation_type, COUNT(*) as count FROM operation_logs "
            + "WHERE created_at BETWEEN #{startTime} AND #{endTime} "
            + "GROUP BY operation_type ORDER BY count DESC")
    List<OperationLog> countByOperationType(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定用户最后登录记录
     *
     * @param userId 用户ID
     * @return 最后登录的操作日志
     */
    @Select("SELECT * FROM operation_logs WHERE user_id = #{userId} AND operation_type = 'LOGIN' ORDER BY created_at DESC LIMIT 1")
    OperationLog selectLastLoginByUserId(@Param("userId") Long userId);

    /**
     * 删除指定时间之前的日志（用于日志清理）
     *
     * @param beforeTime 时间点
     * @return 删除的记录数
     */
    @Select("DELETE FROM operation_logs WHERE created_at < #{beforeTime}")
    int deleteBeforeTime(@Param("beforeTime") LocalDateTime beforeTime);
}
