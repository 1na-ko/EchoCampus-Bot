package com.echocampus.bot.controller;

import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.common.Result;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.entity.KnowledgeCategory;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库控制器
 */
@Tag(name = "Knowledge", description = "知识库管理接口")
@RestController
@RequestMapping("/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "上传文档", description = "上传知识库文档")
    @PostMapping("/docs")
    public Result<KnowledgeDoc> uploadDocument(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文档标题") @RequestParam String title,
            @Parameter(description = "文档描述") @RequestParam(required = false) String description,
            @Parameter(description = "文档分类") @RequestParam(required = false) String category,
            @Parameter(description = "标签") @RequestParam(required = false) String tags,
            @Parameter(description = "上传者ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {

        KnowledgeDocRequest request = new KnowledgeDocRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setCategory(category);
        request.setTags(tags);

        KnowledgeDoc doc = knowledgeService.uploadDocument(file, request, userId);
        return Result.success("文档上传成功，正在处理中...", doc);
    }

    @Operation(summary = "获取文档列表", description = "分页查询知识库文档")
    @GetMapping("/docs")
    public Result<PageResult<KnowledgeDoc>> getDocuments(
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size) {
        PageResult<KnowledgeDoc> result = knowledgeService.getDocuments(category, status, keyword, page, size);
        return Result.success(result);
    }

    @Operation(summary = "获取文档详情", description = "获取指定文档的详细信息")
    @GetMapping("/docs/{docId}")
    public Result<KnowledgeDoc> getDocument(
            @Parameter(description = "文档ID") @PathVariable Long docId) {
        KnowledgeDoc doc = knowledgeService.getDocumentById(docId);
        return Result.success(doc);
    }

    @Operation(summary = "更新文档", description = "更新文档信息")
    @PutMapping("/docs/{docId}")
    public Result<Void> updateDocument(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @Valid @RequestBody KnowledgeDocRequest request) {
        knowledgeService.updateDocument(docId, request);
        return Result.success();
    }

    @Operation(summary = "删除文档", description = "删除指定文档")
    @DeleteMapping("/docs/{docId}")
    public Result<Void> deleteDocument(
            @Parameter(description = "文档ID") @PathVariable Long docId) {
        knowledgeService.deleteDocument(docId);
        return Result.success();
    }

    @Operation(summary = "重新索引文档", description = "重新解析并索引文档")
    @PostMapping("/docs/{docId}/reindex")
    public Result<Void> reindexDocument(
            @Parameter(description = "文档ID") @PathVariable Long docId) {
        knowledgeService.reindexDocument(docId);
        return Result.success();
    }

    @Operation(summary = "获取分类列表", description = "获取所有知识分类")
    @GetMapping("/categories")
    public Result<List<KnowledgeCategory>> getCategories() {
        List<KnowledgeCategory> categories = knowledgeService.getCategories();
        return Result.success(categories);
    }
}
