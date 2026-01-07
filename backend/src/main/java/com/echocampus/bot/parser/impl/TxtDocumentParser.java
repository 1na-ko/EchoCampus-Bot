package com.echocampus.bot.parser.impl;

import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * TXT文档解析器
 */
@Slf4j
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public String parse(String filePath) throws DocumentParseException {
        try {
            Path path = Paths.get(filePath);
            
            // 尝试UTF-8编码
            String content;
            try {
                content = Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 尝试GBK编码（中文Windows常用）
                content = Files.readString(path, Charset.forName("GBK"));
            }
            
            log.info("TXT解析成功: 文件={}, 文本长度={}", filePath, content.length());
            
            return content.trim();
            
        } catch (IOException e) {
            log.error("TXT解析失败: {}", filePath, e);
            throw new DocumentParseException("TXT解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
        try {
            Path path = Paths.get(filePath);
            
            return DocumentMetadata.builder()
                .title(path.getFileName().toString())
                .fileSize(Files.size(path))
                .build();
                
        } catch (IOException e) {
            log.error("获取TXT元数据失败: {}", filePath, e);
            throw new DocumentParseException("获取TXT元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return Arrays.asList("txt");
    }
}
