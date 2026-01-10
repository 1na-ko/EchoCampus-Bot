package com.echocampus.bot.service.impl;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.DocumentParserFactory;
import com.echocampus.bot.parser.exception.DocumentParseException;
import com.echocampus.bot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档异步处理服务实现类
 * 将文档处理逻辑独立出来，确保 @Async 注解能正常工作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements DocumentProcessService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final DocumentParserFactory parserFactory;
    private final TextChunkService textChunkService;
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final DocumentProgressService documentProgressService;

    @Override
    @Async("documentProcessExecutor")
    public void processDocumentAsync(Long docId) {
        log.info("开始异步处理文档: docId={}", docId);
        
        try {
            KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
            if (doc == null) {
                log.warn("文档不存在: docId={}", docId);
                documentProgressService.sendFailed(docId, "INIT", "文档不存在");
                return;
            }
            
            // 更新状态为处理中
            knowledgeDocMapper.updateProcessStatus(docId, "PROCESSING", null);
            
            // 1. 解析文档内容
            log.info("步骤1: 解析文档 - {}", doc.getFilePath());
            documentProgressService.sendParsingProgress(docId, 0, "开始解析文档...");
            
            DocumentParser parser = parserFactory.getParser(doc.getFileType());
            documentProgressService.sendParsingProgress(docId, 30, "已选择解析器: " + doc.getFileType());
            
            String content = parser.parse(doc.getFilePath());
            
            if (content == null || content.trim().isEmpty()) {
                throw new DocumentParseException("文档内容为空");
            }
            log.info("文档解析完成: 内容长度={}", content.length());
            documentProgressService.sendParsingProgress(docId, 100, "解析完成，内容长度: " + content.length() + " 字符");
            
            // 2. 文本切块
            log.info("步骤2: 文本切块");
            documentProgressService.sendChunkingProgress(docId, 0, 0);
            
            List<KnowledgeChunk> chunks = textChunkService.chunkText(content, docId, doc.getFileType());
            
            if (chunks.isEmpty()) {
                throw new RuntimeException("文本切块结果为空");
            }
            log.info("文本切块完成: 切块数量={}", chunks.size());
            documentProgressService.sendChunkingProgress(docId, 50, chunks.size());
            
            // 保存切块到数据库
            for (int i = 0; i < chunks.size(); i++) {
                knowledgeChunkMapper.insert(chunks.get(i));
                // 每保存10个切块更新一次进度
                if ((i + 1) % 10 == 0 || i == chunks.size() - 1) {
                    int progress = 50 + (int) ((i + 1) * 50.0 / chunks.size());
                    documentProgressService.sendChunkingProgress(docId, progress, i + 1);
                }
            }
            documentProgressService.sendChunkingProgress(docId, 100, chunks.size());
            
            // 3. 向量化
            log.info("步骤3: 向量化处理");
            documentProgressService.sendEmbeddingProgress(docId, 0, 0, chunks.size());
            
            List<String> texts = chunks.stream()
                    .map(KnowledgeChunk::getContent)
                    .collect(Collectors.toList());
            
            // 分批向量化以便追踪进度
            List<float[]> vectors = new ArrayList<>();
            int batchSize = 10;
            for (int i = 0; i < texts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, texts.size());
                List<String> batch = texts.subList(i, end);
                List<float[]> batchVectors = embeddingService.embedBatch(batch);
                vectors.addAll(batchVectors);
                
                int progress = (int) (end * 100.0 / texts.size());
                documentProgressService.sendEmbeddingProgress(docId, progress, end, texts.size());
            }
            log.info("向量化完成: 向量数量={}", vectors.size());
            
            // 4. 存入Milvus
            log.info("步骤4: 存入Milvus向量数据库");
            documentProgressService.sendStoringProgress(docId, 0, "准备存储向量数据...");
            
            List<Long> chunkIds = chunks.stream()
                    .map(KnowledgeChunk::getId)
                    .collect(Collectors.toList());
            List<Long> docIds = chunks.stream()
                    .map(c -> docId)
                    .collect(Collectors.toList());
            List<String> categories = chunks.stream()
                    .map(c -> doc.getCategory() != null ? doc.getCategory() : "default")
                    .collect(Collectors.toList());
            
            documentProgressService.sendStoringProgress(docId, 30, "正在连接向量数据库...");
            
            milvusService.insertVectors(vectors, chunkIds, docIds, texts, categories);
            
            documentProgressService.sendStoringProgress(docId, 80, "向量数据已存储");
            
            // 更新文档状态
            doc.setVectorCount(chunks.size());
            knowledgeDocMapper.updateById(doc);
            knowledgeDocMapper.updateProcessStatus(docId, "COMPLETED", 
                    String.format("处理成功: %d个切块", chunks.size()));
            
            documentProgressService.sendStoringProgress(docId, 100, "数据库记录已更新");
            
            log.info("文档处理完成: docId={}, 切块数={}", docId, chunks.size());
            
            // 发送完成状态
            documentProgressService.sendCompleted(docId, chunks.size());
            
        } catch (DocumentParseException e) {
            log.error("文档解析失败: docId={}", docId, e);
            knowledgeDocMapper.updateProcessStatus(docId, "FAILED", "解析失败: " + e.getMessage());
            documentProgressService.sendFailed(docId, "PARSING", "解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文档处理失败: docId={}", docId, e);
            knowledgeDocMapper.updateProcessStatus(docId, "FAILED", e.getMessage());
            documentProgressService.sendFailed(docId, "PROCESSING", e.getMessage());
        }
    }
}
