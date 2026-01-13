package com.echocampus.bot.controller;

import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.dto.response.DocumentProgressDTO;
import com.echocampus.bot.entity.KnowledgeCategory;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.service.DocumentProgressService;
import com.echocampus.bot.service.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KnowledgeController 控制器测试
 * P2 优先级 - API契约验证
 */
@WebMvcTest(KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false) // 禁用安全过滤器
@DisplayName("KnowledgeController - 知识库控制器测试")
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeService knowledgeService;

    @MockBean
    private DocumentProgressService documentProgressService;

    private KnowledgeDoc testDoc;
    private KnowledgeDocRequest docRequest;

    @BeforeEach
    void setUp() {
        testDoc = createTestDoc();
        docRequest = createDocRequest();
    }

    private KnowledgeDoc createTestDoc() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setTitle("测试文档");
        doc.setDescription("测试描述");
        doc.setFileName("test.pdf");
        doc.setFilePath("/uploads/test.pdf");
        doc.setFileSize(1024L);
        doc.setFileType("pdf");
        doc.setCategory("技术文档");
        doc.setTags("测试,文档");
        doc.setStatus("ACTIVE");
        doc.setProcessStatus("COMPLETED");
        doc.setVectorCount(100);
        doc.setCreatedBy(1L);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    private KnowledgeDocRequest createDocRequest() {
        KnowledgeDocRequest request = new KnowledgeDocRequest();
        request.setTitle("测试文档");
        request.setDescription("测试描述");
        request.setCategory("技术文档");
        request.setTags("测试,文档");
        return request;
    }

    @Nested
    @DisplayName("上传文档接口测试 - POST /v1/knowledge/docs")
    class UploadDocumentTests {

        @Test
        @DisplayName("应该成功上传文档")
        void shouldUploadDocumentSuccessfully() throws Exception {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "test content".getBytes()
            );
            
            when(knowledgeService.uploadDocument(any(), any(KnowledgeDocRequest.class), anyLong()))
                .thenReturn(testDoc);

            // Act & Assert
            mockMvc.perform(multipart("/v1/knowledge/docs")
                    .file(file)
                    .param("title", "测试文档")
                    .param("description", "测试描述")
                    .param("category", "技术文档")
                    .param("tags", "测试,文档")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试文档"))
                .andExpect(jsonPath("$.data.fileType").value("pdf"));
        }

        @Test
        @DisplayName("不支持的文件类型应该返回业务错误")
        void shouldReturnErrorForUnsupportedFileType() throws Exception {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/x-msdownload", "test".getBytes()
            );
            
            when(knowledgeService.uploadDocument(any(), any(KnowledgeDocRequest.class), anyLong()))
                .thenThrow(new BusinessException(ResultCode.UNSUPPORTED_FILE_TYPE, "不支持的文件类型"));

            // Act & Assert
            mockMvc.perform(multipart("/v1/knowledge/docs")
                    .file(file)
                    .param("title", "测试")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNSUPPORTED_FILE_TYPE.getCode()));
        }

        @Test
        @DisplayName("文件过大应该返回业务错误")
        void shouldReturnErrorForLargeFile() throws Exception {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "test".getBytes()
            );
            
            when(knowledgeService.uploadDocument(any(), any(KnowledgeDocRequest.class), anyLong()))
                .thenThrow(new BusinessException(ResultCode.DOC_UPLOAD_FAILED, "文件过大"));

            // Act & Assert
            mockMvc.perform(multipart("/v1/knowledge/docs")
                    .file(file)
                    .param("title", "测试")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DOC_UPLOAD_FAILED.getCode()));
        }
    }

    @Nested
    @DisplayName("获取文档列表接口测试 - GET /v1/knowledge/docs")
    class GetDocumentsTests {

        @Test
        @DisplayName("应该成功获取文档列表")
        void shouldGetDocumentsSuccessfully() throws Exception {
            // Arrange
            PageResult<KnowledgeDoc> pageResult = PageResult.of(1L, 1, 10, List.of(testDoc));
            when(knowledgeService.getDocuments(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs")
                    .requestAttr("userId", 1L)
                    .param("page", "1")
                    .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        }

        @Test
        @DisplayName("应该支持按分类过滤")
        void shouldSupportCategoryFilter() throws Exception {
            // Arrange
            PageResult<KnowledgeDoc> pageResult = PageResult.of(1L, 1, 10, List.of(testDoc));
            when(knowledgeService.getDocuments(eq("技术文档"), any(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs")
                    .requestAttr("userId", 1L)
                    .param("category", "技术文档"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(knowledgeService).getDocuments(eq("技术文档"), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("应该支持关键词搜索")
        void shouldSupportKeywordSearch() throws Exception {
            // Arrange
            PageResult<KnowledgeDoc> pageResult = PageResult.of(0L, 1, 10, Collections.emptyList());
            when(knowledgeService.getDocuments(any(), any(), eq("测试"), anyInt(), anyInt()))
                .thenReturn(pageResult);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs")
                    .requestAttr("userId", 1L)
                    .param("keyword", "测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(knowledgeService).getDocuments(any(), any(), eq("测试"), anyInt(), anyInt());
        }

        @Test
        @DisplayName("使用默认分页参数")
        void shouldUseDefaultPaginationParams() throws Exception {
            // Arrange
            PageResult<KnowledgeDoc> pageResult = PageResult.of(0L, 1, 10, Collections.emptyList());
            when(knowledgeService.getDocuments(any(), any(), any(), eq(1), eq(10)))
                .thenReturn(pageResult);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk());

            verify(knowledgeService).getDocuments(any(), any(), any(), eq(1), eq(10));
        }
    }

    @Nested
    @DisplayName("获取文档详情接口测试 - GET /v1/knowledge/docs/{docId}")
    class GetDocumentTests {

        @Test
        @DisplayName("应该成功获取文档详情")
        void shouldGetDocumentSuccessfully() throws Exception {
            // Arrange
            when(knowledgeService.getDocumentById(1L)).thenReturn(testDoc);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs/1")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试文档"))
                .andExpect(jsonPath("$.data.fileType").value("pdf"));
        }

        @Test
        @DisplayName("文档不存在应该返回业务错误")
        void shouldReturnErrorWhenDocumentNotFound() throws Exception {
            // Arrange
            when(knowledgeService.getDocumentById(999L))
                .thenThrow(new BusinessException(ResultCode.DOC_NOT_FOUND));

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs/999")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DOC_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("更新文档接口测试 - PUT /v1/knowledge/docs/{docId}")
    class UpdateDocumentTests {

        @Test
        @DisplayName("应该成功更新文档")
        void shouldUpdateDocumentSuccessfully() throws Exception {
            // Arrange
            doNothing().when(knowledgeService).updateDocument(anyLong(), any(KnowledgeDocRequest.class));

            // Act & Assert
            mockMvc.perform(put("/v1/knowledge/docs/1")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(knowledgeService).updateDocument(eq(1L), any(KnowledgeDocRequest.class));
        }

        @Test
        @DisplayName("更新不存在的文档应该返回业务错误")
        void shouldReturnErrorWhenUpdatingNonExistentDocument() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.DOC_NOT_FOUND))
                .when(knowledgeService).updateDocument(eq(999L), any(KnowledgeDocRequest.class));

            // Act & Assert
            mockMvc.perform(put("/v1/knowledge/docs/999")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DOC_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("删除文档接口测试 - DELETE /v1/knowledge/docs/{docId}")
    class DeleteDocumentTests {

        @Test
        @DisplayName("应该成功删除文档")
        void shouldDeleteDocumentSuccessfully() throws Exception {
            // Arrange
            doNothing().when(knowledgeService).deleteDocument(1L);

            // Act & Assert
            mockMvc.perform(delete("/v1/knowledge/docs/1")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(knowledgeService).deleteDocument(1L);
        }

        @Test
        @DisplayName("删除不存在的文档应该返回业务错误")
        void shouldReturnErrorWhenDeletingNonExistentDocument() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.DOC_NOT_FOUND))
                .when(knowledgeService).deleteDocument(999L);

            // Act & Assert
            mockMvc.perform(delete("/v1/knowledge/docs/999")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DOC_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("重新索引文档接口测试 - POST /v1/knowledge/docs/{docId}/reindex")
    class ReindexDocumentTests {

        @Test
        @DisplayName("应该成功触发重新索引")
        void shouldReindexDocumentSuccessfully() throws Exception {
            // Arrange
            doNothing().when(knowledgeService).reindexDocument(1L);

            // Act & Assert
            mockMvc.perform(post("/v1/knowledge/docs/1/reindex")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(knowledgeService).reindexDocument(1L);
        }

        @Test
        @DisplayName("重新索引不存在的文档应该返回业务错误")
        void shouldReturnErrorWhenReindexingNonExistentDocument() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.DOC_NOT_FOUND))
                .when(knowledgeService).reindexDocument(999L);

            // Act & Assert
            mockMvc.perform(post("/v1/knowledge/docs/999/reindex")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.DOC_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("获取分类列表接口测试 - GET /v1/knowledge/categories")
    class GetCategoriesTests {

        @Test
        @DisplayName("应该成功获取分类列表")
        void shouldGetCategoriesSuccessfully() throws Exception {
            // Arrange
            KnowledgeCategory category = new KnowledgeCategory();
            category.setId(1L);
            category.setName("技术文档");
            
            when(knowledgeService.getCategories()).thenReturn(List.of(category));

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/categories")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("技术文档"));
        }

        @Test
        @DisplayName("没有分类时应该返回空数组")
        void shouldReturnEmptyArrayWhenNoCategories() throws Exception {
            // Arrange
            when(knowledgeService.getCategories()).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/categories")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Nested
    @DisplayName("获取文档处理进度接口测试 - GET /v1/knowledge/docs/{docId}/progress/current")
    class GetProgressTests {

        @Test
        @DisplayName("应该成功获取文档处理进度")
        void shouldGetProgressSuccessfully() throws Exception {
            // Arrange
            DocumentProgressDTO progress = new DocumentProgressDTO();
            progress.setDocId(1L);
            progress.setStatus("PROCESSING");
            progress.setProgress(50);
            progress.setCurrentStep("解析文档");
            
            when(documentProgressService.getOrBuildProgress(1L)).thenReturn(progress);

            // Act & Assert
            mockMvc.perform(get("/v1/knowledge/docs/1/progress/current")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.docId").value(1))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.progress").value(50));
        }
    }
}
