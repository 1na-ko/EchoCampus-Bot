package com.echocampus.bot.parser;

import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;

import java.util.List;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    /**
     * 解析文档，提取文本内容
     *
     * @param filePath 文件路径
     * @return 提取的文本内容
     * @throws DocumentParseException 解析异常
     */
    String parse(String filePath) throws DocumentParseException;

    /**
     * 获取文档元数据
     *
     * @param filePath 文件路径
     * @return 文档元数据
     * @throws DocumentParseException 解析异常
     */
    DocumentMetadata getMetadata(String filePath) throws DocumentParseException;

    /**
     * 支持的文件类型
     *
     * @return 文件类型列表（如 ["pdf", "txt", "md"]）
     */
    List<String> getSupportedTypes();
}
