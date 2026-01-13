package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Word文档解析器 (DOCX格式)
 */
@Slf4j
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        validateFilePath(filePath);
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            StringBuilder text = new StringBuilder();
            
            // 提取段落文本
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText.trim()).append("\n");
                }
            }
            
            // 提取表格文本
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            rowText.append(cellText.trim()).append("\t");
                        }
                    }
                    if (rowText.length() > 0) {
                        text.append(rowText.toString().trim()).append("\n");
                    }
                }
                text.append("\n");
            }
            
            String result = text.toString().trim();
            log.info("DOCX解析成功: 文件={}, 文本长度={}", filePath, result.length());
            
            return result;
            
        } catch (IOException e) {
            log.error("DOCX解析失败: {}", filePath, e);
            throw new DocumentParseException("DOCX解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        validateFilePath(filePath);
        
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            String title = null;
            String author = null;
            
            try {
                var coreProps = document.getProperties().getCoreProperties();
                title = coreProps.getTitle();
                author = coreProps.getCreator();
            } catch (Exception e) {
                log.debug("无法获取DOCX核心属性: {}", e.getMessage());
            }
            
            if (title == null || title.isEmpty()) {
                title = new File(filePath).getName();
            }
            
            // 计算页数（估算：每页约3000字符）
            StringBuilder text = new StringBuilder();
            
            // 提取段落文本
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    text.append(paragraphText.trim()).append("\n");
                }
            }
            
            // 提取表格文本
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            rowText.append(cellText.trim()).append("\t");
                        }
                    }
                    if (rowText.length() > 0) {
                        text.append(rowText.toString().trim()).append("\n");
                    }
                }
            }
            
            String content = text.toString().trim();
            int estimatedPages = Math.max(1, content.length() / 3000);
            
            return DocumentMetadata.builder()
                .title(title)
                .author(author)
                .pageCount(estimatedPages)
                .fileSize(new File(filePath).length())
                .characterCount(content.length())
                .build();
                
        } catch (IOException e) {
            log.error("获取DOCX元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取DOCX元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Collections.singletonList("docx");
    }
}
