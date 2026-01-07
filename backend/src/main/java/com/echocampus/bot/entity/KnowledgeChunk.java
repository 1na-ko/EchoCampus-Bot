package com.echocampus.bot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 知识库文档片段实体类
 */
@Data
@TableName(value = "knowledge_chunks", autoResultMap = true)
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档ID
     */
    private Long docId;

    /**
     * 片段在文档中的位置索引
     */
    private Integer chunkIndex;

    /**
     * 片段类型：TEXT, TITLE, CODE
     */
    private String chunkType;

    /**
     * 原始文本内容
     */
    private String content;

    /**
     * 内容哈希（用于去重）
     */
    private String contentHash;

    /**
     * Milvus中的向量ID
     */
    private String vectorId;

    /**
     * PDF页码（仅PDF文档）
     */
    private Integer pageNumber;

    /**
     * 元数据（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * Token数量
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
