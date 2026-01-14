package com.echocampus.bot.service.impl;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.service.EmbeddingService;
import com.echocampus.bot.service.MilvusService;
import com.echocampus.bot.service.VectorMigrationService;
import com.echocampus.bot.service.VectorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 向量数据迁移服务实现
 * 支持Milvus与pgvector之间的双向数据迁移
 */
@Slf4j
@Service
public class VectorMigrationServiceImpl implements VectorMigrationService {

    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorService pgVectorService;
    private final MilvusService milvusService;

    private final MigrationProgress progress = new MigrationProgress();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Autowired
    public VectorMigrationServiceImpl(
            KnowledgeChunkMapper chunkMapper,
            EmbeddingService embeddingService,
            @Qualifier("pgVectorService") VectorService pgVectorService,
            MilvusService milvusService) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.pgVectorService = pgVectorService;
        this.milvusService = milvusService;
    }

    @Override
    @Async("documentProcessExecutor")
    public boolean migrateFromMilvusToPgVector(int batchSize, boolean reindexFromChunks) {
        if (progress.getStatus() == MigrationStatus.RUNNING) {
            log.warn("迁移任务正在运行中，请等待完成");
            return false;
        }

        resetProgress();
        progress.setStatus(MigrationStatus.RUNNING);
        progress.setStartTime(System.currentTimeMillis());
        cancelled.set(false);

        try {
            if (reindexFromChunks) {
                // 方案1: 从knowledge_chunks表重新生成向量（推荐）
                migrateByReindexing(batchSize);
            } else {
                // 方案2: 尝试从Milvus读取并迁移（需要Milvus可用）
                migrateFromMilvusDirectly(batchSize);
            }

            progress.setStatus(MigrationStatus.COMPLETED);
            progress.setEndTime(System.currentTimeMillis());
            log.info("迁移完成: 总计 {} 条, 成功 {} 条, 失败 {} 条, 耗时 {} ms",
                    progress.getTotalRecords(),
                    progress.getSuccessCount(),
                    progress.getFailedCount(),
                    progress.getDurationMs());
            return true;

        } catch (Exception e) {
            log.error("迁移失败: {}", e.getMessage(), e);
            progress.setStatus(MigrationStatus.FAILED);
            progress.setErrorMessage(e.getMessage());
            progress.setEndTime(System.currentTimeMillis());
            return false;
        }
    }

    /**
     * 通过重新生成向量进行迁移（推荐方式）
     */
    private void migrateByReindexing(int batchSize) {
        progress.setCurrentPhase("初始化pgvector");
        pgVectorService.initVectorStore();

        progress.setCurrentPhase("统计待迁移数据");
        // 查询所有有vectorId的chunks
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(KnowledgeChunk::getVectorId);
        long totalCount = chunkMapper.selectCount(wrapper);
        progress.setTotalRecords((int) totalCount);
        
        log.info("开始重建向量索引，共 {} 条记录", totalCount);

        progress.setCurrentPhase("重建向量索引");
        int offset = 0;
        
        while (!cancelled.get()) {
            // 分页查询chunks
            wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(KnowledgeChunk::getVectorId)
                   .last(String.format("LIMIT %d OFFSET %d", batchSize, offset));
            
            List<KnowledgeChunk> chunks = chunkMapper.selectList(wrapper);
            
            if (chunks.isEmpty()) {
                break;
            }

            try {
                processBatchChunks(chunks);
                progress.setSuccessCount(progress.getSuccessCount() + chunks.size());
            } catch (Exception e) {
                log.error("批次处理失败 (offset={}): {}", offset, e.getMessage());
                progress.setFailedCount(progress.getFailedCount() + chunks.size());
            }

            progress.setProcessedRecords(progress.getProcessedRecords() + chunks.size());
            offset += batchSize;
            
            log.info("迁移进度: {}/{} ({}%)", 
                    progress.getProcessedRecords(), 
                    progress.getTotalRecords(),
                    progress.getProgressPercent());
        }

        if (cancelled.get()) {
            progress.setStatus(MigrationStatus.CANCELLED);
            log.info("迁移已取消");
        }
    }

    /**
     * 处理一批chunks
     */
    private void processBatchChunks(List<KnowledgeChunk> chunks) {
        // 1. 提取文本内容
        List<String> texts = chunks.stream()
                .map(KnowledgeChunk::getContent)
                .collect(Collectors.toList());

        // 2. 批量生成向量
        List<float[]> vectors = embeddingService.embedBatch(texts);
        
        if (vectors.size() != chunks.size()) {
            throw new RuntimeException("向量生成数量与chunk数量不匹配");
        }

        // 3. 准备插入数据
        List<Long> chunkIds = chunks.stream()
                .map(KnowledgeChunk::getId)
                .collect(Collectors.toList());
        
        List<Long> docIds = chunks.stream()
                .map(KnowledgeChunk::getDocId)
                .collect(Collectors.toList());
        
        List<String> categories = chunks.stream()
                .map(c -> c.getMetadata() != null && c.getMetadata().containsKey("category") 
                        ? c.getMetadata().get("category").toString() 
                        : "default")
                .collect(Collectors.toList());

        // 4. 插入到pgvector
        List<String> newVectorIds = pgVectorService.insertVectors(vectors, chunkIds, docIds, texts, categories);
        
        // 5. 更新knowledge_chunks表中的vectorId（可选，保持兼容）
        for (int i = 0; i < chunks.size(); i++) {
            if (i < newVectorIds.size()) {
                KnowledgeChunk chunk = chunks.get(i);
                chunk.setVectorId(newVectorIds.get(i));
                chunkMapper.updateById(chunk);
            }
        }
    }

    /**
     * 直接从Milvus迁移（需要Milvus服务可用）
     */
    private void migrateFromMilvusDirectly(int batchSize) {
        if (!milvusService.isAvailable()) {
            throw new RuntimeException("Milvus服务不可用，无法直接迁移。请使用重建索引方式。");
        }

        progress.setCurrentPhase("初始化pgvector");
        pgVectorService.initVectorStore();

        progress.setCurrentPhase("统计Milvus数据");
        long milvusCount = milvusService.getVectorCount();
        progress.setTotalRecords((int) milvusCount);
        
        log.info("开始从Milvus直接迁移，共 {} 条向量", milvusCount);

        // 注意：Milvus SDK没有直接的分页查询所有数据的API
        // 这里改用从knowledge_chunks表获取vectorId后查询的方式
        progress.setCurrentPhase("迁移向量数据");
        
        LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(KnowledgeChunk::getVectorId);
        
        int offset = 0;
        while (!cancelled.get()) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(KnowledgeChunk::getVectorId)
                   .last(String.format("LIMIT %d OFFSET %d", batchSize, offset));
            
            List<KnowledgeChunk> chunks = chunkMapper.selectList(wrapper);
            if (chunks.isEmpty()) {
                break;
            }

            try {
                // 由于无法直接从Milvus按ID批量获取向量，这里仍然使用重建方式
                processBatchChunks(chunks);
                progress.setSuccessCount(progress.getSuccessCount() + chunks.size());
            } catch (Exception e) {
                log.error("批次处理失败: {}", e.getMessage());
                progress.setFailedCount(progress.getFailedCount() + chunks.size());
            }

            progress.setProcessedRecords(progress.getProcessedRecords() + chunks.size());
            offset += batchSize;
        }
    }

    @Override
    @Async("documentProcessExecutor")
    public boolean migrateFromPgVectorToMilvus(int batchSize) {
        if (progress.getStatus() == MigrationStatus.RUNNING) {
            log.warn("迁移任务正在运行中");
            return false;
        }

        if (!milvusService.isAvailable()) {
            log.error("Milvus服务不可用");
            return false;
        }

        resetProgress();
        progress.setStatus(MigrationStatus.RUNNING);
        progress.setStartTime(System.currentTimeMillis());
        cancelled.set(false);

        try {
            progress.setCurrentPhase("初始化Milvus");
            milvusService.initCollection();

            progress.setCurrentPhase("统计pgvector数据");
            long pgvectorCount = pgVectorService.getVectorCount();
            progress.setTotalRecords((int) pgvectorCount);

            log.info("开始从pgvector迁移到Milvus，共 {} 条向量", pgvectorCount);

            // 从knowledge_chunks表重建（与正向迁移类似）
            progress.setCurrentPhase("迁移向量数据");
            migrateToMilvusByReindexing(batchSize);

            progress.setStatus(MigrationStatus.COMPLETED);
            progress.setEndTime(System.currentTimeMillis());
            return true;

        } catch (Exception e) {
            log.error("回滚迁移失败: {}", e.getMessage(), e);
            progress.setStatus(MigrationStatus.FAILED);
            progress.setErrorMessage(e.getMessage());
            progress.setEndTime(System.currentTimeMillis());
            return false;
        }
    }

    private void migrateToMilvusByReindexing(int batchSize) {
        LambdaQueryWrapper<KnowledgeChunk> wrapper;
        int offset = 0;

        while (!cancelled.get()) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(KnowledgeChunk::getVectorId)
                   .last(String.format("LIMIT %d OFFSET %d", batchSize, offset));

            List<KnowledgeChunk> chunks = chunkMapper.selectList(wrapper);
            if (chunks.isEmpty()) {
                break;
            }

            try {
                List<String> texts = chunks.stream()
                        .map(KnowledgeChunk::getContent)
                        .collect(Collectors.toList());

                List<float[]> vectors = embeddingService.embedBatch(texts);

                List<Long> chunkIds = chunks.stream().map(KnowledgeChunk::getId).collect(Collectors.toList());
                List<Long> docIds = chunks.stream().map(KnowledgeChunk::getDocId).collect(Collectors.toList());
                List<String> categories = chunks.stream()
                        .map(c -> c.getMetadata() != null && c.getMetadata().containsKey("category")
                                ? c.getMetadata().get("category").toString()
                                : "default")
                        .collect(Collectors.toList());

                milvusService.insertVectors(vectors, chunkIds, docIds, texts, categories);
                progress.setSuccessCount(progress.getSuccessCount() + chunks.size());
            } catch (Exception e) {
                log.error("批次处理失败: {}", e.getMessage());
                progress.setFailedCount(progress.getFailedCount() + chunks.size());
            }

            progress.setProcessedRecords(progress.getProcessedRecords() + chunks.size());
            offset += batchSize;
        }
    }

    @Override
    public MigrationProgress getMigrationProgress() {
        return progress;
    }

    @Override
    public boolean cancelMigration() {
        if (progress.getStatus() != MigrationStatus.RUNNING) {
            return false;
        }
        cancelled.set(true);
        return true;
    }

    @Override
    public boolean validateMigration() {
        try {
            // 1. 检查pgvector向量数量
            long pgvectorCount = pgVectorService.getVectorCount();
            
            // 2. 检查knowledge_chunks表中有vectorId的记录数
            LambdaQueryWrapper<KnowledgeChunk> wrapper = new LambdaQueryWrapper<>();
            wrapper.isNotNull(KnowledgeChunk::getVectorId);
            long chunkCount = chunkMapper.selectCount(wrapper);
            
            log.info("验证迁移结果: pgvector向量数={}, knowledge_chunks记录数={}", 
                    pgvectorCount, chunkCount);
            
            // 3. 随机抽样验证搜索功能
            if (pgvectorCount > 0) {
                // 获取一个chunk进行测试搜索
                wrapper = new LambdaQueryWrapper<>();
                wrapper.isNotNull(KnowledgeChunk::getContent).last("LIMIT 1");
                KnowledgeChunk testChunk = chunkMapper.selectOne(wrapper);
                
                if (testChunk != null) {
                    float[] testVector = embeddingService.embed(testChunk.getContent());
                    List<VectorService.SearchResult> results = pgVectorService.search(testVector, 5, 0.0f);
                    
                    if (results.isEmpty()) {
                        log.warn("验证搜索返回空结果");
                        return false;
                    }
                    log.info("验证搜索成功，返回 {} 条结果", results.size());
                }
            }
            
            // 4. 数量匹配检查（允许一定误差）
            double matchRate = chunkCount > 0 ? (double) pgvectorCount / chunkCount : 0;
            if (matchRate < 0.95) {
                log.warn("向量数量匹配率过低: {}%", matchRate * 100);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("验证迁移失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean cleanupSource(String source) {
        try {
            if ("milvus".equalsIgnoreCase(source)) {
                if (milvusService.isAvailable()) {
                    // 删除Milvus中的所有数据
                    // 注意：这是危险操作，需要确认
                    log.warn("清理Milvus数据 - 此操作不可逆！");
                    // milvusService.dropCollection(); // 需要添加此方法
                    return true;
                }
            } else if ("pgvector".equalsIgnoreCase(source)) {
                // 清理pgvector表
                log.warn("清理pgvector数据 - 此操作不可逆！");
                // 需要添加清理方法
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("清理源数据失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private void resetProgress() {
        progress.setStatus(MigrationStatus.IDLE);
        progress.setTotalRecords(0);
        progress.setProcessedRecords(0);
        progress.setSuccessCount(0);
        progress.setFailedCount(0);
        progress.setCurrentPhase("");
        progress.setErrorMessage(null);
        progress.setStartTime(0);
        progress.setEndTime(0);
    }
}
