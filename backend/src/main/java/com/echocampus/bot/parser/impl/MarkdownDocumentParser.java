package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Markdown文档解析器
 */
@Slf4j
@Component
public class MarkdownDocumentParser implements DocumentParser {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    @Override
    public String parse(String filePath) throws DocumentParseException {
        try {
            String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            
            // 解析Markdown为HTML
            Node document = parser.parse(content);
            String html = renderer.render(document);
            
            // 从HTML提取纯文本
            String text = extractTextFromHtml(html);
            
            log.info("Markdown解析成功: 文件={}, 文本长度={}", filePath, text.length());
            
            return text;
            
        } catch (IOException e) {
            log.error("Markdown解析失败: {}", filePath, e);
            throw new DocumentParseException("Markdown解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        try {
            String content = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            
            String title = extractTitle(content);
            
            return DocumentMetadata.builder()
                .title(title)
                .fileSize(new File(filePath).length())
                .characterCount(content.length())
                .build();
                
        } catch (IOException e) {
            log.error("获取Markdown元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取Markdown元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Arrays.asList("md", "markdown");
    }

    /**
     * 从HTML中提取纯文本
     */
    private String extractTextFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        
        // 将代码块替换为标记
        Elements codeBlocks = doc.select("pre code");
        codeBlocks.forEach(block -> block.text("[代码块]"));
        
        // 提取文本并清理
        String text = doc.text();
        
        // 清理多余空白
        text = text.replaceAll(" +", " ");
        
        return text.trim();
    }

    /**
     * 从Markdown中提取标题
     */
    private String extractTitle(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return "未命名文档";
    }
}
