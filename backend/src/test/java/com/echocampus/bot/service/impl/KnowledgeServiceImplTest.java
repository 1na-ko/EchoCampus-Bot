package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.parser.DocumentParser;
import com.echocampus.bot.parser.DocumentParserFactory;
import com.echocampus.bot.parser.dto.DocumentMetadata;
import com.echocampus.bot.service.MilvusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;

    @Mock
    private DocumentParserFactory documentParserFactory;

    @Mock
    private DocumentParser documentParser;

    @Mock
    private MilvusService milvusService;

    @InjectMocks
    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(knowledgeService, "uploadPath", "./uploads");
        ReflectionTestUtils.setField(knowledgeService, "allowedTypes", "pdf,txt,md,docx");
        ReflectionTestUtils.setField(knowledgeService, "maxFileSize", 52428800L);
    }

    @Test
    void testUploadDocument_WithEmptyFile_ShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        KnowledgeDocRequest request = new KnowledgeDocRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            knowledgeService.uploadDocument(file, request, 1L);
        });

        assertEquals(ResultCode.DOC_UPLOAD_FAILED.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("文件为空"));
    }

    @Test
    void testUploadDocument_WithLargeFile_ShouldThrowException() {
        byte[] largeContent = new byte[52428801];
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", largeContent);
        KnowledgeDocRequest request = new KnowledgeDocRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            knowledgeService.uploadDocument(file, request, 1L);
        });

        assertEquals(ResultCode.DOC_UPLOAD_FAILED.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("文件过大"));
    }

    @Test
    void testUploadDocument_WithUnsupportedFileType_ShouldThrowException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/x-msdownload", "test".getBytes());
        KnowledgeDocRequest request = new KnowledgeDocRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            knowledgeService.uploadDocument(file, request, 1L);
        });

        assertEquals(ResultCode.UNSUPPORTED_FILE_TYPE.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
    }
}
