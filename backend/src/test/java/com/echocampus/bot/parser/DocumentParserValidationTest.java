package com.echocampus.bot.parser;

import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.parser.exception.DocumentParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserValidationTest {

    @TempDir
    Path tempDir;

    @Test
    void testValidateFilePath_WithNullPath_ShouldThrowException() {
        DocumentParser parser = createTestParser();

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath(null);
        });

        assertTrue(exception.getMessage().contains("文件路径不能为空"));
    }

    @Test
    void testValidateFilePath_WithEmptyPath_ShouldThrowException() {
        DocumentParser parser = createTestParser();

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath("");
        });

        assertTrue(exception.getMessage().contains("文件路径不能为空"));
    }

    @Test
    void testValidateFilePath_WithNonExistentFile_ShouldThrowException() {
        DocumentParser parser = createTestParser();

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath("/non/existent/file.txt");
        });

        assertTrue(exception.getMessage().contains("文件不存在"));
    }

    @Test
    void testValidateFilePath_WithDirectory_ShouldThrowException() throws IOException {
        DocumentParser parser = createTestParser();
        Path dir = tempDir.resolve("testdir");
        Files.createDirectory(dir);

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath(dir.toString());
        });

        assertTrue(exception.getMessage().contains("路径不是文件"));
    }

    @Test
    void testValidateFilePath_WithEmptyFile_ShouldThrowException() throws IOException {
        DocumentParser parser = createTestParser();
        Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath(file.toString());
        });

        assertTrue(exception.getMessage().contains("文件为空"));
    }

    @Test
    void testValidateFilePath_WithValidFile_ShouldNotThrowException() throws IOException {
        DocumentParser parser = createTestParser();
        Path file = tempDir.resolve("valid.txt");
        Files.writeString(file, "test content");

        assertDoesNotThrow(() -> {
            parser.validateFilePath(file.toString());
        });
    }

    @Test
    void testValidateFilePath_WithLargeFile_ShouldThrowException() throws IOException {
        DocumentParser parser = createTestParser();
        Path file = tempDir.resolve("large.txt");
        byte[] largeContent = new byte[101 * 1024 * 1024];
        Files.write(file, largeContent);

        DocumentParseException exception = assertThrows(DocumentParseException.class, () -> {
            parser.validateFilePath(file.toString());
        });

        assertTrue(exception.getMessage().contains("文件过大"));
    }

    private DocumentParser createTestParser() {
        return new DocumentParser() {
            @Override
            public String parse(String filePath) throws DocumentParseException {
                return null;
            }

            @Override
            public DocumentMetadata getMetadata(String filePath) throws DocumentParseException {
                return null;
            }

            @Override
            public java.util.List<String> getSupportedTypes() {
                return java.util.List.of("txt");
            }
        };
    }
}
