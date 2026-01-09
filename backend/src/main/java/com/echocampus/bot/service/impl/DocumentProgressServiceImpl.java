package com.echocampus.bot.service.impl;

import com.echocampus.bot.dto.response.DocumentProgressDTO;
import com.echocampus.bot.service.DocumentProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档处理进度服务实现类
 * 使用SSE (Server-Sent Events) 实现实时进度推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProgressServiceImpl implements DocumentProgressService {

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
     * 注册SSE发射器
     * @param docId 文档ID
     * @return SseEmitter
     */
    public SseEmitter registerEmitter(Long docId) {
        // SSE超时时间设为30分钟，足够处理大文档
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成: docId={}", docId);
            emitters.remove(docId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: docId={}", docId);
            emitters.remove(docId);
        });
        
        emitter.onError((e) -> {
            log.error("SSE连接错误: docId={}, error={}", docId, e.getMessage());
            emitters.remove(docId);
        });
        
        // 移除旧的发射器
        SseEmitter oldEmitter = emitters.put(docId, emitter);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                // 忽略完成时的错误
            }
        }
        
        log.info("注册SSE发射器: docId={}", docId);
        
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
    public void clearProgress(Long docId) {
        progressCache.remove(docId);
        removeEmitter(docId);
    }
}
