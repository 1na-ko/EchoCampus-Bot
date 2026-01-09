package com.echocampus.bot.service;

/**
 * 文档异步处理服务接口
 * 用于处理文档的解析、切块、向量化等耗时操作
 */
public interface DocumentProcessService {

    /**
     * 异步处理文档（解析、切块、向量化）
     * @param docId 文档ID
     */
    void processDocumentAsync(Long docId);
}
