package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * PDF文档解析器
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        validateFilePath(filePath);
        
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            
            String text = stripper.getText(document);
            
            log.info("PDF解析成功: 文件={}, 页数={}, 文本长度={}", 
                filePath, document.getNumberOfPages(), text.length());
            
            return cleanText(text);
            
        } catch (IOException e) {
            log.error("PDF解析失败: {}", filePath, e);
            throw new DocumentParseException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        validateFilePath(filePath);
        
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDDocumentInformation info = document.getDocumentInformation();
            
            return DocumentMetadata.builder()
                .title(info.getTitle())
                .author(info.getAuthor())
                .subject(info.getSubject())
                .keywords(info.getKeywords())
                .pageCount(document.getNumberOfPages())
                .fileSize(new File(filePath).length())
                .createdDate(formatCalendar(info.getCreationDate()))
                .modifiedDate(formatCalendar(info.getModificationDate()))
                .build();
                
        } catch (IOException e) {
            log.error("获取PDF元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取PDF元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Arrays.asList("pdf");
    }

    /**
     * 清理文本内容
     */
    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 统一换行符
        text = text.replaceAll("\\r\\n", "\n");
        // 移除多余空白
        text = text.replaceAll(" +", " ");
        // 移除控制字符
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        // 合并多个空行为两个
        text = text.replaceAll("\n{3,}", "\n\n");
        
        return text.trim();
    }
    
    private String formatCalendar(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        return calendar.getTime().toString();
    }
}
