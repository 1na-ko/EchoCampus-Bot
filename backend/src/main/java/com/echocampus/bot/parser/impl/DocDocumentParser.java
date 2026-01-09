package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Word文档解析器 (DOC格式 - Word 97-2003)
 */
@Slf4j
@Component
public class DocDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            
            // 使用 WordExtractor 提取所有文本内容
            String text = extractor.getText();
            
            // 清理文本（移除多余的空行）
            String result = text.replaceAll("(?m)^[ \t]*\r?\n", "\n")
                               .replaceAll("\n{3,}", "\n\n")
                               .trim();
            
            log.info("DOC解析成功: 文件={}, 文本长度={}", filePath, result.length());
            
            return result;
            
        } catch (IOException e) {
            log.error("DOC解析失败: {}", filePath, e);
            throw new DocumentParseException("DOC解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis)) {
            
            File file = new File(filePath);
            String title = file.getName();
            String author = null;
            
            // 尝试获取文档属性
            try {
                var summaryInfo = document.getSummaryInformation();
                if (summaryInfo != null) {
                    String docTitle = summaryInfo.getTitle();
                    if (docTitle != null && !docTitle.trim().isEmpty()) {
                        title = docTitle;
                    }
                    author = summaryInfo.getAuthor();
                }
            } catch (Exception e) {
                log.debug("无法获取DOC核心属性: {}", e.getMessage());
            }
            
            // 计算页数（估算：每页约3000字符）
            String content = parse(filePath);
            int estimatedPages = Math.max(1, content.length() / 3000);
            
            return DocumentMetadata.builder()
                .title(title)
                .author(author)
                .pageCount(estimatedPages)
                .fileSize(file.length())
                .characterCount(content.length())
                .build();
                
        } catch (IOException e) {
            log.error("获取DOC元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取DOC元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Collections.singletonList("doc");
    }
}
