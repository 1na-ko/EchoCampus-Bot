package com.echocampus.bot.parser;

import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;

import java.io.File;
import java.util.List;

/**
 * 文档解析器接口
 */
public interface DocumentParser {

    long MAX_FILE_SIZE = 100 * 1024 * 1024;

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

    /**
     * 验证文件路径参数
     *
     * @param filePath 文件路径
     * @throws DocumentParseException 验证失败异常
     */
    default void validateFilePath(String filePath) throws DocumentParseException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new DocumentParseException("文件路径不能为空");
        }

        File file = new File(filePath);
        if (!file.exists()) {
            throw new DocumentParseException("文件不存在: " + filePath);
        }

        if (!file.isFile()) {
            throw new DocumentParseException("路径不是文件: " + filePath);
        }

        if (!file.canRead()) {
            throw new DocumentParseException("文件不可读: " + filePath);
        }

        if (file.length() > MAX_FILE_SIZE) {
            throw new DocumentParseException("文件过大: " + file.length() + " bytes，最大允许: " + MAX_FILE_SIZE + " bytes");
        }

        if (file.length() == 0) {
            throw new DocumentParseException("文件为空: " + filePath);
        }
    }
}
