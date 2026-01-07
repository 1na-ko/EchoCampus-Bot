package com.echocampus.bot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档实体类
 */
@Data
@TableName("knowledge_docs")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档描述
     */
    private String description;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型：pdf, txt, md, docx, doc, ppt, pptx
     */
    private String fileType;

    /**
     * 知识分类
     */
    private String category;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 文档状态：ACTIVE, INACTIVE, PROCESSING, FAILED
     */
    private String status;

    /**
     * 向量数量
     */
    private Integer vectorCount;

    /**
     * 处理状态：PENDING, PROCESSING, COMPLETED, FAILED
     */
    private String processStatus;

    /**
     * 处理结果信息
     */
    private String processMessage;

    /**
     * 最后索引时间
     */
    private LocalDateTime lastIndexedAt;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
