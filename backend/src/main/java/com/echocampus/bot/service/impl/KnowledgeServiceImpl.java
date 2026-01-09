package com.echocampus.bot.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.entity.KnowledgeCategory;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.mapper.KnowledgeCategoryMapper;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.service.DocumentProcessService;
import com.echocampus.bot.service.KnowledgeService;
import com.echocampus.bot.service.MilvusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;


/**
 * 知识库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final MilvusService milvusService;
    private final DocumentProcessService documentProcessService;

    @Value("${document.upload-path:./uploads}")
    private String uploadPath;

    @Value("${document.allowed-types:pdf,txt,md,docx,doc,ppt,pptx}")
    private String allowedTypes;

    @Override
    @Transactional
    public KnowledgeDoc uploadDocument(MultipartFile file, KnowledgeDocRequest request, Long userId) {
        // 1. 验证文件类型
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        
        if (!isAllowedType(fileType)) {
            throw new BusinessException(ResultCode.UNSUPPORTED_FILE_TYPE, 
                    "不支持的文件类型: " + fileType + "，支持的类型: " + allowedTypes);
        }

        // 2. 保存文件
        String savedFileName = UUID.randomUUID() + "." + fileType;
        Path uploadDir = Paths.get(uploadPath);
        
        try {
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path filePath = uploadDir.resolve(savedFileName);
            file.transferTo(filePath.toFile());
            
            // 3. 保存文档记录
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setTitle(request.getTitle());
            doc.setDescription(request.getDescription());
            doc.setFileName(originalFilename);
            doc.setFilePath(filePath.toString());
            doc.setFileSize(file.getSize());
            doc.setFileType(fileType);
            doc.setCategory(request.getCategory());
            doc.setTags(request.getTags());
            doc.setStatus("ACTIVE");
            doc.setProcessStatus("PENDING");
            doc.setVectorCount(0);
            doc.setCreatedBy(userId);
            
            knowledgeDocMapper.insert(doc);
            
            // 4. 在事务提交后异步处理文档（避免异步线程读取到未提交的数据）
            final Long docId = doc.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentProcessService.processDocumentAsync(docId);
                }
            });
            
            log.info("文档上传成功: docId={}, title={}", doc.getId(), doc.getTitle());
            return doc;
            
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ResultCode.DOC_UPLOAD_FAILED, "文件保存失败: " + e.getMessage());
        }
    }

    @Override
    public PageResult<KnowledgeDoc> getDocuments(String category, String status, String keyword, Integer page, Integer size) {
        Page<KnowledgeDoc> pageParam = new Page<>(page, size);
        IPage<KnowledgeDoc> result = knowledgeDocMapper.selectPageByCondition(pageParam, category, status, keyword);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    @Override
    public KnowledgeDoc getDocumentById(Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(ResultCode.DOC_NOT_FOUND);
        }
        return doc;
    }

    @Override
    @Transactional
    public void updateDocument(Long docId, KnowledgeDocRequest request) {
        KnowledgeDoc doc = getDocumentById(docId);
        
        if (request.getTitle() != null) {
            doc.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            doc.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            doc.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            doc.setTags(request.getTags());
        }
        
        knowledgeDocMapper.updateById(doc);
    }

    @Override
    @Transactional
    public void deleteDocument(Long docId) {
        KnowledgeDoc doc = getDocumentById(docId);
        
        // 1. 删除Milvus中的向量
        try {
            milvusService.deleteByDocId(docId);
            log.info("已删除文档 {} 的向量数据", docId);
        } catch (Exception e) {
            log.warn("删除Milvus向量失败: {}", e.getMessage());
        }
        
        // 2. 删除数据库中的文本切块
        try {
            knowledgeChunkMapper.deleteByDocId(docId);
            log.info("已删除文档 {} 的切块数据", docId);
        } catch (Exception e) {
            log.warn("删除文本切块失败: {}", e.getMessage());
        }
        
        // 3. 删除物理文件
        try {
            Path filePath = Paths.get(doc.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("已删除物理文件: {}", doc.getFilePath());
            }
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", e.getMessage());
        }
        
        // 4. 删除数据库记录
        knowledgeDocMapper.deleteById(docId);
        
        log.info("文档已彻底删除: docId={}, title={}", docId, doc.getTitle());
    }

    @Override
    @Transactional
    public void reindexDocument(Long docId) {
        KnowledgeDoc doc = getDocumentById(docId);
        doc.setProcessStatus("PENDING");
        knowledgeDocMapper.updateById(doc);
        
        // 在事务提交后异步重新处理（避免异步线程读取到未提交的数据）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessService.processDocumentAsync(docId);
            }
        });
    }

    @Override
    public List<KnowledgeCategory> getCategories() {
        return knowledgeCategoryMapper.selectTopLevel();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedType(String fileType) {
        String[] types = allowedTypes.split(",");
        for (String type : types) {
            if (type.trim().equalsIgnoreCase(fileType)) {
                return true;
            }
        }
        return false;
    }
}

