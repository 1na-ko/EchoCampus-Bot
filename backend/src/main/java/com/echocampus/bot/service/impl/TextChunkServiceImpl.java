package com.echocampus.bot.service.impl;

import com.echocampus.bot.entity.KnowledgeChunk;
import com.echocampus.bot.service.TextChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本切块服务实现
 * 采用递归分割策略，根据不同文档类型使用不同的切块参数
 */
@Slf4j
@Service
public class TextChunkServiceImpl implements TextChunkService {

    @Value("${chunking.max-size:500}")
    private int defaultMaxSize;

    @Value("${chunking.overlap-size:50}")
    private int defaultOverlapSize;

    // 分隔符优先级：段落 > 句子 > 短语 > 单词
    private static final String[] SEPARATORS = {
        "\n\n",     // 段落分隔
        "\n",       // 换行
        "。",       // 中文句号
        "！",       // 中文感叹号
        "？",       // 中文问号
        ".",        // 英文句号
        "!",        // 英文感叹号
        "?",        // 英文问号
        "；",       // 中文分号
        ";",        // 英文分号
        "，",       // 中文逗号
        ",",        // 英文逗号
        " "         // 空格
    };

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
     * 执行切块操作
     */
    private List<KnowledgeChunk> doChunk(String text, Long docId, int maxSize, int overlapSize) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 预处理文本
        text = preprocessText(text);
        
        // 递归分割
        List<String> textChunks = recursiveSplit(text, maxSize, overlapSize, 0);
        
        // 创建KnowledgeChunk对象
        int position = 0;
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i).trim();
            
            if (chunkContent.isEmpty()) {
                continue;
            }
            
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkContent);
            chunk.setStartPosition(position);
            chunk.setEndPosition(position + chunkContent.length());
            chunk.setTokenCount(estimateTokenCount(chunkContent));
            
            chunks.add(chunk);
            position += chunkContent.length();
        }
        
        log.info("文本切块完成: docId={}, 原文长度={}, 切块数={}, maxSize={}, overlap={}", 
            docId, text.length(), chunks.size(), maxSize, overlapSize);
        
        return chunks;
    }

    /**
     * 递归分割文本
     */
    private List<String> recursiveSplit(String text, int maxSize, int overlapSize, int separatorIndex) {
        List<String> result = new ArrayList<>();
        
        // 如果文本已经足够小，直接返回
        if (text.length() <= maxSize) {
            if (!text.trim().isEmpty()) {
                result.add(text);
            }
            return result;
        }
        
        // 如果没有更多分隔符，强制按长度分割
        if (separatorIndex >= SEPARATORS.length) {
            return forceSplit(text, maxSize, overlapSize);
        }
        
        String separator = SEPARATORS[separatorIndex];
        String[] parts = text.split(Pattern.quote(separator), -1);
        
        // 如果分割没有效果，尝试下一个分隔符
        if (parts.length == 1) {
            return recursiveSplit(text, maxSize, overlapSize, separatorIndex + 1);
        }
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            // 如果当前部分加上分隔符会超过最大长度
            if (currentChunk.length() + part.length() + separator.length() > maxSize) {
                // 保存当前块
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString());
                    
                    // 添加重叠部分
                    if (overlapSize > 0 && currentChunk.length() > overlapSize) {
                        String overlap = currentChunk.substring(currentChunk.length() - overlapSize);
                        currentChunk = new StringBuilder(overlap);
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
                
                // 如果单个部分就超过最大长度，递归处理
                if (part.length() > maxSize) {
                    List<String> subChunks = recursiveSplit(part, maxSize, overlapSize, separatorIndex + 1);
                    result.addAll(subChunks);
                    continue;
                }
            }
            
            // 添加分隔符（除了第一个部分）
            if (currentChunk.length() > 0 && !separator.equals(" ")) {
                currentChunk.append(separator);
            }
            currentChunk.append(part);
        }
        
        // 添加最后一个块
        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString());
        }
        
        return result;
    }

    /**
     * 强制按长度分割（最后的手段）
     */
    private List<String> forceSplit(String text, int maxSize, int overlapSize) {
        List<String> result = new ArrayList<>();
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            result.add(text.substring(start, end));
            start = end - overlapSize;
            if (start < 0) start = 0;
            if (start >= text.length()) break;
        }
        
        return result;
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
