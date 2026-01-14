package com.echocampus.bot.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.common.PageResult;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.KnowledgeDocRequest;
import com.echocampus.bot.entity.KnowledgeCategory;
import com.echocampus.bot.entity.KnowledgeDoc;
import com.echocampus.bot.mapper.KnowledgeCategoryMapper;
import com.echocampus.bot.mapper.KnowledgeChunkMapper;
import com.echocampus.bot.mapper.KnowledgeDocMapper;
import com.echocampus.bot.service.DocumentProcessService;
import com.echocampus.bot.service.MilvusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgeServiceImpl 单元测试
 * P1 优先级 - 知识库核心业务
 * 
 * 注意：由于 MyBatis-Plus BaseMapper 与 Mockito 的兼容性问题，这些测试暂时被禁用。
 * 建议改用集成测试（@SpringBootTest）或使用内存数据库进行测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeServiceImpl - 知识库服务测试")
@Disabled("MyBatis-Plus BaseMapper 与 Mockito 存在兼容性问题，需要改用集成测试")
class KnowledgeServiceImplTest {

    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;

    @Mock
    private KnowledgeCategoryMapper knowledgeCategoryMapper;

    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Mock
    private MilvusService milvusService;

    @Mock
    private DocumentProcessService documentProcessService;

    @InjectMocks
    private KnowledgeServiceImpl knowledgeService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(knowledgeService, "uploadPath", tempDir.toString());
        ReflectionTestUtils.setField(knowledgeService, "allowedTypes", "pdf,txt,md,markdown,docx,doc,pptx,ppt,xlsx,xls");
        ReflectionTestUtils.setField(knowledgeService, "maxFileSize", 104857600L); // 100MB
    }

    private KnowledgeDoc createTestDoc() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setTitle("测试文档");
        doc.setDescription("测试描述");
        doc.setFileName("test.pdf");
        doc.setFilePath(tempDir.resolve("test.pdf").toString());
        doc.setFileSize(1024L);
        doc.setFileType("pdf");
        doc.setCategory("技术文档");
        doc.setStatus("ACTIVE");
        doc.setProcessStatus("COMPLETED");
        doc.setVectorCount(100);
        doc.setCreatedBy(1L);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    @Nested
    @DisplayName("文档上传测试")
    class UploadDocumentTests {

        @Test
        @DisplayName("应该成功上传有效文档")
        void shouldUploadValidDocument() throws IOException {
            // Arrange
            byte[] content = "test content".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", content
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("测试文档");
            request.setDescription("测试描述");
            request.setCategory("技术");

            when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
                KnowledgeDoc doc = invocation.getArgument(0);
                doc.setId(1L);
                return 1;
            });

            // Act
            KnowledgeDoc result = knowledgeService.uploadDocument(file, request, 1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("测试文档");
            assertThat(result.getFileType()).isEqualTo("pdf");
            assertThat(result.getProcessStatus()).isEqualTo("PENDING");
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getCreatedBy()).isEqualTo(1L);

            verify(knowledgeDocMapper).insert(any(KnowledgeDoc.class));
        }

        @Test
        @DisplayName("空文件应该抛出异常")
        void shouldThrowExceptionForEmptyFile() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[0]
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.uploadDocument(file, request, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_UPLOAD_FAILED.getCode());
                    assertThat(be.getMessage()).contains("文件为空");
                });

            verify(knowledgeDocMapper, never()).insert(any(KnowledgeDoc.class));
        }

        @Test
        @DisplayName("超大文件应该抛出异常")
        void shouldThrowExceptionForLargeFile() {
            // Arrange - 设置较小的最大文件大小用于测试
            ReflectionTestUtils.setField(knowledgeService, "maxFileSize", 100L);
            
            byte[] largeContent = new byte[200];
            MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", largeContent
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.uploadDocument(file, request, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_UPLOAD_FAILED.getCode());
                    assertThat(be.getMessage()).contains("文件过大");
                });
        }

        @ParameterizedTest
        @DisplayName("不支持的文件类型应该抛出异常")
        @ValueSource(strings = {"test.exe", "test.bat", "test.sh", "test.js", "test.php"})
        void shouldThrowExceptionForUnsupportedFileTypes(String filename) {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/octet-stream", "test".getBytes()
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.uploadDocument(file, request, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.UNSUPPORTED_FILE_TYPE.getCode());
                });
        }

        @ParameterizedTest
        @DisplayName("支持的文件类型应该上传成功")
        @ValueSource(strings = {"test.pdf", "test.txt", "test.md", "test.docx", "test.doc", "test.pptx", "test.xlsx"})
        void shouldAcceptSupportedFileTypes(String filename) {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/octet-stream", "test content".getBytes()
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("测试");

            when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
                KnowledgeDoc doc = invocation.getArgument(0);
                doc.setId(1L);
                return 1;
            });

            // Act
            KnowledgeDoc result = knowledgeService.uploadDocument(file, request, 1L);

            // Assert
            assertThat(result).isNotNull();
            verify(knowledgeDocMapper).insert(any(KnowledgeDoc.class));
        }

        @Test
        @DisplayName("应该正确提取文件扩展名")
        void shouldExtractFileExtension() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "document.test.PDF", "application/pdf", "test".getBytes()
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("测试");

            when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
                KnowledgeDoc doc = invocation.getArgument(0);
                doc.setId(1L);
                return 1;
            });

            // Act
            KnowledgeDoc result = knowledgeService.uploadDocument(file, request, 1L);

            // Assert
            assertThat(result.getFileType()).isEqualTo("pdf");
        }

        @Test
        @DisplayName("应该保存原始文件名")
        void shouldSaveOriginalFilename() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "原始文件名.pdf", "application/pdf", "test".getBytes()
            );
            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("测试");

            when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
                KnowledgeDoc doc = invocation.getArgument(0);
                doc.setId(1L);
                return 1;
            });

            // Act
            KnowledgeDoc result = knowledgeService.uploadDocument(file, request, 1L);

            // Assert
            assertThat(result.getFileName()).isEqualTo("原始文件名.pdf");
        }
    }

    @Nested
    @DisplayName("文档查询测试")
    class GetDocumentsTests {

        @Test
        @DisplayName("应该成功分页查询文档")
        void shouldGetDocumentsWithPagination() {
            // Arrange
            List<KnowledgeDoc> docs = List.of(createTestDoc());
            Page<KnowledgeDoc> page = new Page<>(1, 10);
            page.setRecords(docs);
            page.setTotal(1);

            when(knowledgeDocMapper.selectPageByCondition(any(Page.class), anyString(), anyString(), anyString()))
                .thenReturn(page);

            // Act
            PageResult<KnowledgeDoc> result = knowledgeService.getDocuments("技术文档", "ACTIVE", "测试", 1, 10);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getList()).hasSize(1);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("应该根据ID获取文档")
        void shouldGetDocumentById() {
            // Arrange
            KnowledgeDoc doc = createTestDoc();
            when(knowledgeDocMapper.selectById(1L)).thenReturn(doc);

            // Act
            KnowledgeDoc result = knowledgeService.getDocumentById(1L);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTitle()).isEqualTo("测试文档");
        }

        @Test
        @DisplayName("文档不存在应该抛出异常")
        void shouldThrowExceptionWhenDocumentNotFound() {
            // Arrange
            when(knowledgeDocMapper.selectById(999L)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.getDocumentById(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_NOT_FOUND.getCode());
                });
        }

        @ParameterizedTest
        @DisplayName("不同分页参数应该正确查询")
        @CsvSource({
            "1, 10",
            "2, 20",
            "1, 50",
            "5, 5"
        })
        void shouldHandleDifferentPaginationParams(int page, int size) {
            // Arrange
            Page<KnowledgeDoc> mockPage = new Page<>(page, size);
            mockPage.setRecords(new ArrayList<>());
            mockPage.setTotal(0);

            when(knowledgeDocMapper.selectPageByCondition(any(Page.class), any(), any(), any()))
                .thenReturn(mockPage);

            // Act
            PageResult<KnowledgeDoc> result = knowledgeService.getDocuments(null, null, null, page, size);

            // Assert
            assertThat(result.getPage()).isEqualTo(page);
            assertThat(result.getSize()).isEqualTo(size);
        }
    }

    @Nested
    @DisplayName("文档更新测试")
    class UpdateDocumentTests {

        @Test
        @DisplayName("应该成功更新文档信息")
        void shouldUpdateDocument() {
            // Arrange
            KnowledgeDoc existingDoc = createTestDoc();
            when(knowledgeDocMapper.selectById(1L)).thenReturn(existingDoc);

            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("更新后标题");
            request.setDescription("更新后描述");
            request.setCategory("新分类");
            request.setTags("标签1,标签2");

            // Act
            knowledgeService.updateDocument(1L, request);

            // Assert
            ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
            verify(knowledgeDocMapper).updateById(docCaptor.capture());
            
            KnowledgeDoc updatedDoc = docCaptor.getValue();
            assertThat(updatedDoc.getTitle()).isEqualTo("更新后标题");
            assertThat(updatedDoc.getDescription()).isEqualTo("更新后描述");
            assertThat(updatedDoc.getCategory()).isEqualTo("新分类");
            assertThat(updatedDoc.getTags()).isEqualTo("标签1,标签2");
        }

        @Test
        @DisplayName("部分更新应该只更新非null字段")
        void shouldPartiallyUpdateDocument() {
            // Arrange
            KnowledgeDoc existingDoc = createTestDoc();
            existingDoc.setTitle("原标题");
            existingDoc.setDescription("原描述");
            when(knowledgeDocMapper.selectById(1L)).thenReturn(existingDoc);

            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("新标题"); // 只更新标题

            // Act
            knowledgeService.updateDocument(1L, request);

            // Assert
            ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
            verify(knowledgeDocMapper).updateById(docCaptor.capture());
            
            KnowledgeDoc updatedDoc = docCaptor.getValue();
            assertThat(updatedDoc.getTitle()).isEqualTo("新标题");
            assertThat(updatedDoc.getDescription()).isEqualTo("原描述"); // 保持不变
        }

        @Test
        @DisplayName("更新不存在的文档应该抛出异常")
        void shouldThrowExceptionWhenUpdatingNonExistentDocument() {
            // Arrange
            when(knowledgeDocMapper.selectById(999L)).thenReturn(null);

            KnowledgeDocRequest request = new KnowledgeDocRequest();
            request.setTitle("新标题");

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.updateDocument(999L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_NOT_FOUND.getCode());
                });
        }
    }

    @Nested
    @DisplayName("文档删除测试")
    class DeleteDocumentTests {

        @Test
        @DisplayName("应该成功删除文档及相关数据")
        void shouldDeleteDocumentAndRelatedData() throws IOException {
            // Arrange
            Path testFile = tempDir.resolve("test-delete.pdf");
            Files.write(testFile, "test".getBytes());
            
            KnowledgeDoc doc = createTestDoc();
            doc.setFilePath(testFile.toString());
            when(knowledgeDocMapper.selectById(1L)).thenReturn(doc);

            // Act
            knowledgeService.deleteDocument(1L);

            // Assert
            verify(milvusService).deleteByDocId(1L);
            verify(knowledgeChunkMapper).deleteByDocId(1L);
            verify(knowledgeDocMapper).deleteById(1L);
            assertThat(Files.exists(testFile)).isFalse(); // 物理文件应被删除
        }

        @Test
        @DisplayName("删除不存在的文档应该抛出异常")
        void shouldThrowExceptionWhenDeletingNonExistentDocument() {
            // Arrange
            when(knowledgeDocMapper.selectById(999L)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.deleteDocument(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_NOT_FOUND.getCode());
                });
        }

        @Test
        @DisplayName("Milvus删除失败不应该影响整体删除流程")
        void shouldContinueDeletionWhenMilvusFails() throws IOException {
            // Arrange
            Path testFile = tempDir.resolve("test-milvus-fail.pdf");
            Files.write(testFile, "test".getBytes());
            
            KnowledgeDoc doc = createTestDoc();
            doc.setFilePath(testFile.toString());
            when(knowledgeDocMapper.selectById(1L)).thenReturn(doc);
            doThrow(new RuntimeException("Milvus error")).when(milvusService).deleteByDocId(1L);

            // Act - 不应该抛出异常
            knowledgeService.deleteDocument(1L);

            // Assert - 其他删除操作应该继续执行
            verify(knowledgeChunkMapper).deleteByDocId(1L);
            verify(knowledgeDocMapper).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("文档重新索引测试")
    class ReindexDocumentTests {

        @Test
        @DisplayName("应该成功触发重新索引")
        void shouldReindexDocument() {
            // Arrange
            KnowledgeDoc doc = createTestDoc();
            when(knowledgeDocMapper.selectById(1L)).thenReturn(doc);

            // Act
            knowledgeService.reindexDocument(1L);

            // Assert
            ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
            verify(knowledgeDocMapper).updateById(docCaptor.capture());
            assertThat(docCaptor.getValue().getProcessStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("重新索引不存在的文档应该抛出异常")
        void shouldThrowExceptionWhenReindexingNonExistentDocument() {
            // Arrange
            when(knowledgeDocMapper.selectById(999L)).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> knowledgeService.reindexDocument(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.DOC_NOT_FOUND.getCode());
                });
        }
    }

    @Nested
    @DisplayName("分类管理测试")
    class CategoryManagementTests {

        @Test
        @DisplayName("应该成功获取分类列表")
        void shouldGetCategories() {
            // Arrange
            List<KnowledgeCategory> categories = new ArrayList<>();
            KnowledgeCategory category = new KnowledgeCategory();
            category.setId(1L);
            category.setName("技术文档");
            categories.add(category);
            
            when(knowledgeCategoryMapper.selectTopLevel()).thenReturn(categories);

            // Act
            List<KnowledgeCategory> result = knowledgeService.getCategories();

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("技术文档");
        }

        @Test
        @DisplayName("没有分类时应该返回空列表")
        void shouldReturnEmptyListWhenNoCategories() {
            // Arrange
            when(knowledgeCategoryMapper.selectTopLevel()).thenReturn(new ArrayList<>());

            // Act
            List<KnowledgeCategory> result = knowledgeService.getCategories();

            // Assert
            assertThat(result).isEmpty();
        }
    }
}

