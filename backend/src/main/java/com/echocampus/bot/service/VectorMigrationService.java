package com.echocampus.bot.service;

/**
 * 向量数据迁移服务接口
 * 支持从Milvus迁移到pgvector或反向迁移
 */
public interface VectorMigrationService {

    /**
     * 迁移状态
     */
    enum MigrationStatus {
        IDLE,           // 空闲
        RUNNING,        // 运行中
        COMPLETED,      // 完成
        FAILED,         // 失败
        CANCELLED       // 已取消
    }

    /**
     * 迁移进度信息
     */
    class MigrationProgress {
        private MigrationStatus status;
        private int totalRecords;
        private int processedRecords;
        private int successCount;
        private int failedCount;
        private String currentPhase;
        private String errorMessage;
        private long startTime;
        private long endTime;

        // Getters and Setters
        public MigrationStatus getStatus() { return status; }
        public void setStatus(MigrationStatus status) { this.status = status; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getProcessedRecords() { return processedRecords; }
        public void setProcessedRecords(int processedRecords) { this.processedRecords = processedRecords; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public String getCurrentPhase() { return currentPhase; }
        public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        
        public int getProgressPercent() {
            if (totalRecords == 0) return 0;
            return (int) ((processedRecords * 100.0) / totalRecords);
        }
        
        public long getDurationMs() {
            if (startTime == 0) return 0;
            long end = endTime > 0 ? endTime : System.currentTimeMillis();
            return end - startTime;
        }
    }

    /**
     * 从Milvus迁移到pgvector
     * @param batchSize 批量处理大小
     * @param reindexFromChunks 是否从knowledge_chunks表重新生成向量（true: 重新生成, false: 尝试从Milvus读取）
     * @return 迁移是否启动成功
     */
    boolean migrateFromMilvusToPgVector(int batchSize, boolean reindexFromChunks);

    /**
     * 从pgvector迁移到Milvus（回滚用）
     * @param batchSize 批量处理大小
     * @return 迁移是否启动成功
     */
    boolean migrateFromPgVectorToMilvus(int batchSize);

    /**
     * 获取当前迁移进度
     * @return 迁移进度信息
     */
    MigrationProgress getMigrationProgress();

    /**
     * 取消正在进行的迁移
     * @return 是否成功取消
     */
    boolean cancelMigration();

    /**
     * 验证迁移结果
     * @return 验证是否通过
     */
    boolean validateMigration();

    /**
     * 清理源数据（迁移完成后可选操作）
     * @param source 源类型: "milvus" 或 "pgvector"
     * @return 是否成功清理
     */
    boolean cleanupSource(String source);
}
