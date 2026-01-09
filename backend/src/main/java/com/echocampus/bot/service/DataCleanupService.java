package com.echocampus.bot.service;

/**
 * 数据清理服务接口
 */
public interface DataCleanupService {

    /**
     * 清理过期的软删除数据
     * @return 清理的记录数量
     */
    int cleanupExpiredDeletedData();
}
