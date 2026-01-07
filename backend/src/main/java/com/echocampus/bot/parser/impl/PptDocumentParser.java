package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * PPT文档解析器 (PPTX格式)
 */
@Slf4j
@Component
public class PptDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            
            StringBuilder text = new StringBuilder();
            
            List<XSLFSlide> slides = ppt.getSlides();
            
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                
                text.append("=== 幻灯片 ").append(i + 1).append(" ===\n");
                
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        
                        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                            String paragraphText = paragraph.getText();
                            if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                                text.append(paragraphText.trim()).append("\n");
                            }
                        }
                    }
                }
                
                text.append("\n");
            }
            
            String result = text.toString().trim();
            log.info("PPT解析成功: 文件={}, 幻灯片数={}, 文本长度={}", 
                filePath, slides.size(), result.length());
            
            return result;
            
        } catch (IOException e) {
            log.error("PPT解析失败: {}", filePath, e);
            throw new DocumentParseException("PPT解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        try (FileInputStream fis = new FileInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            
            String title = null;
            
            // 尝试从第一张幻灯片获取标题
            if (!ppt.getSlides().isEmpty()) {
                XSLFSlide firstSlide = ppt.getSlides().get(0);
                for (XSLFShape shape : firstSlide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            title = text.trim().split("\n")[0]; // 取第一行
                            break;
                        }
                    }
                }
            }
            
            if (title == null || title.isEmpty()) {
                title = new File(filePath).getName();
            }
            
            return DocumentMetadata.builder()
                .title(title)
                .slideCount(ppt.getSlides().size())
                .fileSize(new File(filePath).length())
                .build();
                
        } catch (IOException e) {
            log.error("获取PPT元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取PPT元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Arrays.asList("pptx", "ppt");
    }
}
