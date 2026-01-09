package com.echocampus.bot.service;

import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.entity.KnowledgeCategory;
import com.echocampus.bot.entity.KnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {

    /**
     * 上传文档
     * @param file 文件
     * @param request 文档信息
     * @param userId 上传者ID
     * @return 文档对象
     */
    KnowledgeDoc uploadDocument(MultipartFile file, KnowledgeDocRequest request, Long userId);

    /**
     * 分页查询文档列表
     * @param category 分类
     * @param status 状态
     * @param keyword 关键词
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<KnowledgeDoc> getDocuments(String category, String status, String keyword, Integer page, Integer size);

    /**
     * 获取文档详情
     * @param docId 文档ID
     * @return 文档对象
     */
    KnowledgeDoc getDocumentById(Long docId);

    /**
     * 更新文档信息
     * @param docId 文档ID
     * @param request 更新请求
     */
    void updateDocument(Long docId, KnowledgeDocRequest request);

    /**
     * 删除文档
     * @param docId 文档ID
     */
    void deleteDocument(Long docId);

    /**
     * 重新索引文档
     * @param docId 文档ID
     */
    void reindexDocument(Long docId);

    /**
     * 获取所有分类
     * @return 分类列表
     */
    List<KnowledgeCategory> getCategories();
}
