package com.echocampus.bot.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档解析器工厂
 * 根据文件类型自动选择合适的解析器
 */
@Slf4j
@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserMap = new HashMap<>();

    @Autowired
    public DocumentParserFactory(List<DocumentParser> parsers) {
        // 注册所有解析器
        for (DocumentParser parser : parsers) {
            for (String type : parser.getSupportedTypes()) {
                parserMap.put(type.toLowerCase(), parser);
            }
        }
        
        log.info("文档解析器初始化完成，支持格式: {}", parserMap.keySet());
    }

    /**
     * 根据文件类型获取解析器
     *
     * @param fileType 文件类型（扩展名，如 pdf, txt, md）
     * @return 对应的解析器
     * @throws UnsupportedOperationException 如果不支持该类型
     */
    public DocumentParser getParser(String fileType) {
        String type = fileType.toLowerCase().trim();
        
        // 移除可能的点号前缀
        if (type.startsWith(".")) {
            type = type.substring(1);
        }
        
        DocumentParser parser = parserMap.get(type);
        if (parser == null) {
            throw new UnsupportedOperationException("不支持的文件类型: " + fileType + 
                    "，支持的类型: " + getSupportedTypes());
        }
        
        log.debug("使用解析器: {} 处理文件类型: {}", 
            parser.getClass().getSimpleName(), fileType);
        
        return parser;
    }

    /**
     * 检查是否支持该文件类型
     *
     * @param fileType 文件类型
     * @return 是否支持
     */
    public boolean isSupported(String fileType) {
        if (fileType == null) {
            return false;
        }
        String type = fileType.toLowerCase().trim();
        if (type.startsWith(".")) {
            type = type.substring(1);
        }
        return parserMap.containsKey(type);
    }

    /**
     * 获取所有支持的文件类型
     *
     * @return 支持的文件类型列表
     */
    public List<String> getSupportedTypes() {
        return parserMap.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * 根据文件名获取解析器
     *
     * @param fileName 文件名
     * @return 对应的解析器
     */
    public DocumentParser getParserByFileName(String fileName) {
        String extension = getFileExtension(fileName);
        return getParser(extension);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
