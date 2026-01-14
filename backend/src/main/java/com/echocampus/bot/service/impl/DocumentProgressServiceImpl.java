package com.echocampus.bot.service.impl;

import com.echocampus.bot.dto.response.DocumentProgressDTO;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.service.DocumentProgressService;
import com.echocampus.bot.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.echocampus.bot.utils.DateTimeUtil;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档处理进度服务实现类
 * 使用SSE (Server-Sent Events) 实现实时进度推送
 */
@Slf4j
@Service
public class DocumentProgressServiceImpl implements DocumentProgressService {

    private static final int MAX_SSE_CONNECTIONS = 50;
    private static final long SSE_TIMEOUT_MINUTES = 30;

    private final KnowledgeService knowledgeService;

    public DocumentProgressServiceImpl(@Lazy KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 存储每个文档的SSE发射器
     * key: docId, value: SseEmitter
     */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 存储每个文档的当前进度
     * key: docId, value: 当前进度
     */
    private final Map<Long, DocumentProgressDTO> progressCache = new ConcurrentHashMap<>();

    /**
     * 存储SSE连接创建时间
     * key: docId, value: 创建时间
     */
    private final Map<Long, LocalDateTime> connectionTimestamps = new ConcurrentHashMap<>();

    /**
     * 当前SSE连接数
     */
    private final AtomicInteger currentConnections = new AtomicInteger(0);

    /**
     * 注册SSE发射器
     * @param docId 文档ID
     * @return SseEmitter
     */
    public SseEmitter registerEmitter(Long docId) {
        // 检查连接数限制
        int currentCount = currentConnections.get();
        if (currentCount >= MAX_SSE_CONNECTIONS) {
            log.warn("SSE连接数已达上限: current={}, max={}", currentCount, MAX_SSE_CONNECTIONS);
            throw new IllegalStateException("SSE连接数已达上限，请稍后再试");
        }
        
        // SSE超时时间设为30分钟，足够处理大文档
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MINUTES * 60 * 1000L);
        
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成: docId={}", docId);
            emitters.remove(docId);
            connectionTimestamps.remove(docId);
            currentConnections.decrementAndGet();
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: docId={}", docId);
            emitters.remove(docId);
            connectionTimestamps.remove(docId);
            currentConnections.decrementAndGet();
        });
        
        emitter.onError((e) -> {
            log.error("SSE连接错误: docId={}, error={}", docId, e.getMessage());
            emitters.remove(docId);
            connectionTimestamps.remove(docId);
            currentConnections.decrementAndGet();
        });
        
        // 移除旧的发射器
        SseEmitter oldEmitter = emitters.put(docId, emitter);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                // 忽略完成时的错误
            }
        } else {
            // 只有新连接才增加计数
            currentConnections.incrementAndGet();
        }
        
        // 记录连接时间
        connectionTimestamps.put(docId, DateTimeUtil.now());
        
        log.info("注册SSE发射器: docId={}, 当前连接数={}", docId, currentConnections.get());
        
        // 如果有缓存的进度，立即发送
        DocumentProgressDTO cachedProgress = progressCache.get(docId);
        if (cachedProgress != null) {
            sendToEmitter(emitter, cachedProgress);
        }
        
        return emitter;
    }

    /**
     * 移除SSE发射器
     * @param docId 文档ID
     */
    public void removeEmitter(Long docId) {
        SseEmitter emitter = emitters.remove(docId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // 忽略完成时的错误
            }
        }
    }

    @Override
    public void sendProgress(DocumentProgressDTO progress) {
        if (progress == null || progress.getDocId() == null) {
            return;
        }
        
        Long docId = progress.getDocId();
        
        // 更新缓存
        progressCache.put(docId, progress);
        
        // 发送到SSE
        SseEmitter emitter = emitters.get(docId);
        if (emitter != null) {
            sendToEmitter(emitter, progress);
        }
        
        log.debug("发送进度更新: docId={}, stage={}, progress={}, totalProgress={}", 
                docId, progress.getStage(), progress.getProgress(), progress.getTotalProgress());
    }

    /**
     * 发送数据到SSE发射器
     */
    private void sendToEmitter(SseEmitter emitter, DocumentProgressDTO progress) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(progress));
        } catch (IOException e) {
            log.warn("发送SSE消息失败: docId={}, error={}", progress.getDocId(), e.getMessage());
            emitters.remove(progress.getDocId());
        }
    }

    @Override
    public void sendUploadProgress(Long docId, int progress) {
        sendProgress(DocumentProgressDTO.uploading(docId, progress));
    }

    @Override
    public void sendParsingProgress(Long docId, int progress, String details) {
        sendProgress(DocumentProgressDTO.parsing(docId, progress, details));
    }

    @Override
    public void sendChunkingProgress(Long docId, int progress, int chunkCount) {
        sendProgress(DocumentProgressDTO.chunking(docId, progress, chunkCount));
    }

    @Override
    public void sendEmbeddingProgress(Long docId, int progress, int processedCount, int totalCount) {
        sendProgress(DocumentProgressDTO.embedding(docId, progress, processedCount, totalCount));
    }

    @Override
    public void sendStoringProgress(Long docId, int progress, String details) {
        sendProgress(DocumentProgressDTO.storing(docId, progress, details));
    }

    @Override
    public void sendCompleted(Long docId, int vectorCount) {
        DocumentProgressDTO progress = DocumentProgressDTO.completed(docId, vectorCount);
        sendProgress(progress);
        
        // 完成后关闭SSE连接
        SseEmitter emitter = emitters.get(docId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // 忽略
            }
            emitters.remove(docId);
        }
    }

    @Override
    public void sendFailed(Long docId, String stage, String errorMessage) {
        DocumentProgressDTO progress = DocumentProgressDTO.failed(docId, stage, errorMessage);
        sendProgress(progress);
        
        // 失败后关闭SSE连接
        SseEmitter emitter = emitters.get(docId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // 忽略
            }
            emitters.remove(docId);
        }
    }

    @Override
    public DocumentProgressDTO getProgress(Long docId) {
        return progressCache.get(docId);
    }

    @Override
    public DocumentProgressDTO getOrBuildProgress(Long docId) {
        // 先尝试从缓存获取
        DocumentProgressDTO progress = progressCache.get(docId);
        if (progress != null) {
            return progress;
        }
        
        // 如果缓存中没有，则根据文档状态构建进度信息
        KnowledgeDoc doc = knowledgeService.getDocumentById(docId);
        if (doc == null) {
            return null;
        }
        
        String status = doc.getProcessStatus();
        if ("COMPLETED".equals(status)) {
            progress = DocumentProgressDTO.completed(docId, doc.getVectorCount() != null ? doc.getVectorCount() : 0);
        } else if ("FAILED".equals(status)) {
            progress = DocumentProgressDTO.failed(docId, "UNKNOWN", doc.getProcessMessage());
        } else if ("PROCESSING".equals(status)) {
            progress = DocumentProgressDTO.builder()
                    .docId(docId)
                    .stage("PROCESSING")
                    .stageName("处理中")
                    .progress(0)
                    .totalProgress(0)
                    .message("正在处理中...")
                    .completed(false)
                    .failed(false)
                    .build();
        } else {
            // PENDING 或其他状态
            progress = DocumentProgressDTO.builder()
                    .docId(docId)
                    .stage("PENDING")
                    .stageName("等待处理")
                    .progress(0)
                    .totalProgress(0)
                    .message("等待处理...")
                    .completed(false)
                    .failed(false)
                    .build();
        }
        
        return progress;
    }

    @Override
    public void clearProgress(Long docId) {
        progressCache.remove(docId);
        removeEmitter(docId);
    }

    /**
     * 定时清理超时的SSE连接
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupTimeoutConnections() {
        LocalDateTime now = DateTimeUtil.now();
        int cleanedCount = 0;
        
        for (Map.Entry<Long, LocalDateTime> entry : connectionTimestamps.entrySet()) {
            Long docId = entry.getKey();
            LocalDateTime timestamp = entry.getValue();
            
            // 检查是否超时（超过35分钟，比SSE超时时间多5分钟缓冲）
            if (timestamp.plusMinutes(SSE_TIMEOUT_MINUTES + 5).isBefore(now)) {
                log.warn("清理超时SSE连接: docId={}, 连接时间={}", docId, timestamp);
                removeEmitter(docId);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("定时清理完成: 清理了{}个超时连接, 当前连接数={}", cleanedCount, currentConnections.get());
        }
    }

    /**
     * 获取当前SSE连接数
     */
    public int getCurrentConnectionCount() {
        return currentConnections.get();
    }
}
