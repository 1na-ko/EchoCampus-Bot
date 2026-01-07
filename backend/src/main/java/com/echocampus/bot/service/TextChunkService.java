package com.echocampus.bot.service;

import com.echocampus.bot.entity.KnowledgeChunk;

import java.util.List;

/**
 * 文本切块服务接口
 */
public interface TextChunkService {

    /**
     * 将文本切分成多个块
     *
     * @param text 原始文本
     * @param docId 文档ID
     * @param fileType 文件类型（用于选择不同的切块策略）
     * @return 切块列表
     */
    List<KnowledgeChunk> chunkText(String text, Long docId, String fileType);

    /**
     * 将文本切分成多个块（使用默认配置）
     *
     * @param text 原始文本
     * @param docId 文档ID
     * @return 切块列表
     */
    List<KnowledgeChunk> chunkText(String text, Long docId);
}
