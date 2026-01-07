package com.echocampus.bot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库文档上传请求DTO
 */
@Data
public class KnowledgeDocRequest {

    /**
     * 文档标题
     */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200字符")
    private String title;

    /**
     * 文档描述
     */
    @Size(max = 1000, message = "描述长度不能超过1000字符")
    private String description;

    /**
     * 知识分类
     */
    @Size(max = 100, message = "分类长度不能超过100字符")
    private String category;

    /**
     * 标签（逗号分隔）
     */
    @Size(max = 500, message = "标签长度不能超过500字符")
    private String tags;
}
