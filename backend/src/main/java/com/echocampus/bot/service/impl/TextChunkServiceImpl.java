package com.echocampus.bot.service.impl;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.service.TextChunkService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块服务实现
 * 基于 LangChain4j 的文档分割器，支持语义保持
 */
@Slf4j
@Service
public class TextChunkServiceImpl implements TextChunkService {

    @Value("${document.chunking.max-size:500}")
    private int defaultMaxSize;

    @Value("${document.chunking.overlap-size:50}")
    private int defaultOverlapSize;

    @Override
    public List<KnowledgeChunk> chunkText(String text, Long docId, String fileType) {
        // 根据文件类型获取切块参数
        ChunkConfig config = getConfigByFileType(fileType);
        return doChunk(text, docId, config.maxSize, config.overlapSize);
    }

    @Override
    public List<KnowledgeChunk> chunkText(String text, Long docId) {
        return doChunk(text, docId, defaultMaxSize, defaultOverlapSize);
    }

    /**
     * 使用 LangChain4j 执行切块操作
     */
    private List<KnowledgeChunk> doChunk(String text, Long docId, int maxSize, int overlapSize) {
        List<KnowledgeChunk> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 预处理文本
        text = preprocessText(text);

        try {
            // 创建 LangChain4j Document
            Document document = Document.from(text);

            // 使用分层分割策略：
            // 1. 先按段落分割
            // 2. 如果段落太大，再按句子分割
            // 3. 如果句子还太大，按字符分割
            DocumentSplitter splitter = createHierarchicalSplitter(maxSize, overlapSize);

            // 执行分割
            List<TextSegment> segments = splitter.split(document);

            // 转换为 KnowledgeChunk
            int position = 0;
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                String content = segment.text().trim();

                if (content.isEmpty()) {
                    continue;
                }

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(docId);
                chunk.setChunkIndex(i);
                chunk.setContent(content);
                chunk.setStartPosition(position);
                chunk.setEndPosition(position + content.length());
                chunk.setTokenCount(estimateTokenCount(content));

                chunks.add(chunk);
                position += content.length();
            }

            log.info("LangChain4j文本切块完成: docId={}, 原文长度={}, 切块数={}, maxSize={}, overlap={}",
                    docId, text.length(), chunks.size(), maxSize, overlapSize);

        } catch (Exception e) {
            log.error("LangChain4j切块失败，回退到简单分割: {}", e.getMessage());
            // 回退到简单分割
            chunks = fallbackSplit(text, docId, maxSize, overlapSize);
        }

        return chunks;
    }

    /**
     * 创建分层分割器（段落 -> 句子 -> 字符）
     */
    private DocumentSplitter createHierarchicalSplitter(int maxSize, int overlapSize) {
        // 字符级分割器（最后的兜底）
        DocumentSplitter charSplitter = new DocumentByCharacterSplitter(maxSize, overlapSize);

        // 句子级分割器，子分割器为字符级
        DocumentSplitter sentenceSplitter = new DocumentBySentenceSplitter(maxSize, overlapSize, charSplitter);

        // 段落级分割器，子分割器为句子级
        return new DocumentByParagraphSplitter(maxSize, overlapSize, sentenceSplitter);
    }

    /**
     * 回退分割方案（简单按长度分割）
     */
    private List<KnowledgeChunk> fallbackSplit(String text, Long docId, int maxSize, int overlapSize) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            String content = text.substring(start, end).trim();

            if (!content.isEmpty()) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(docId);
                chunk.setChunkIndex(index++);
                chunk.setContent(content);
                chunk.setStartPosition(start);
                chunk.setEndPosition(end);
                chunk.setTokenCount(estimateTokenCount(content));
                chunks.add(chunk);
            }

            start = end - overlapSize;
            if (start <= 0 || start >= text.length()) break;
        }

        log.warn("使用回退分割完成: docId={}, 切块数={}", docId, chunks.size());
        return chunks;
    }

    /**
     * 预处理文本
     */
    private String preprocessText(String text) {
        // 统一换行符
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");

        // 移除连续空行（保留最多两个换行）
        text = text.replaceAll("\n{3,}", "\n\n");

        // 移除行首行尾空白
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.trim()).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 估算token数量（简单估算：中文1字≈1token，英文4字符≈1token）
     */
    private int estimateTokenCount(String text) {
        int chineseCount = 0;
        int otherCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }

        return chineseCount + (otherCount / 4);
    }

    /**
     * 根据文件类型获取切块配置
     */
    private ChunkConfig getConfigByFileType(String fileType) {
        if (fileType == null) {
            return new ChunkConfig(defaultMaxSize, defaultOverlapSize);
        }

        return switch (fileType.toLowerCase()) {
            case "pdf" -> new ChunkConfig(800, 100);
            case "md", "markdown" -> new ChunkConfig(600, 80);
            case "docx", "doc" -> new ChunkConfig(700, 90);
            case "pptx", "ppt" -> new ChunkConfig(400, 50);
            case "txt" -> new ChunkConfig(500, 50);
            default -> new ChunkConfig(defaultMaxSize, defaultOverlapSize);
        };
    }

    /**
     * 切块配置
     */
    private record ChunkConfig(int maxSize, int overlapSize) {}
}
