package com.echocampus.bot.service;

import com.echocampus.bot.dto.response.DocumentProgressDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文档处理进度服务接口
 * 用于追踪和推送文档处理的实时进度
 */
public interface DocumentProgressService {

    /**
     * 注册SSE发射器
     * @param docId 文档ID
     * @return SseEmitter
     */
    SseEmitter registerEmitter(Long docId);

    /**
     * 发送进度更新
     * @param progress 进度信息
     */
    void sendProgress(DocumentProgressDTO progress);

    /**
     * 发送上传进度
     * @param docId 文档ID
     * @param progress 进度百分比
     */
    void sendUploadProgress(Long docId, int progress);

    /**
     * 发送解析进度
     * @param docId 文档ID
     * @param progress 进度百分比
     * @param details 详情
     */
    void sendParsingProgress(Long docId, int progress, String details);

    /**
     * 发送切块进度
     * @param docId 文档ID
     * @param progress 进度百分比
     * @param chunkCount 切块数量
     */
    void sendChunkingProgress(Long docId, int progress, int chunkCount);

    /**
     * 发送向量化进度
     * @param docId 文档ID
     * @param progress 进度百分比
     * @param processedCount 已处理数量
     * @param totalCount 总数量
     */
    void sendEmbeddingProgress(Long docId, int progress, int processedCount, int totalCount);

    /**
     * 发送存储进度
     * @param docId 文档ID
     * @param progress 进度百分比
     * @param details 详情
     */
    void sendStoringProgress(Long docId, int progress, String details);

    /**
     * 发送完成状态
     * @param docId 文档ID
     * @param vectorCount 向量数量
     */
    void sendCompleted(Long docId, int vectorCount);

    /**
     * 发送失败状态
     * @param docId 文档ID
     * @param stage 失败阶段
     * @param errorMessage 错误信息
     */
    void sendFailed(Long docId, String stage, String errorMessage);

    /**
     * 获取当前进度
     * @param docId 文档ID
     * @return 当前进度信息
     */
    DocumentProgressDTO getProgress(Long docId);

    /**
     * 获取或构建当前进度
     * 如果缓存中没有进度信息，则根据文档状态构建
     * @param docId 文档ID
     * @return 当前进度信息
     */
    DocumentProgressDTO getOrBuildProgress(Long docId);

    /**
     * 清除进度缓存
     * @param docId 文档ID
     */
    void clearProgress(Long docId);
}
