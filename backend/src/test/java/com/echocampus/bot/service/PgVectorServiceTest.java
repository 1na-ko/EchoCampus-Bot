package com.echocampus.bot.service;

import com.echocampus.bot.config.PgVectorConfig;
import com.echocampus.bot.config.VectorProviderConfig;
import com.echocampus.bot.service.impl.PgVectorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PgVectorService单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PgVectorService单元测试")
class PgVectorServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PgVectorConfig pgVectorConfig;
    private VectorProviderConfig vectorProviderConfig;
    private PgVectorServiceImpl pgVectorService;

    @BeforeEach
    void setUp() {
        // 初始化配置
        pgVectorConfig = new PgVectorConfig();
        pgVectorConfig.setDimension(1024);
        pgVectorConfig.setTableName("knowledge_vectors");
        pgVectorConfig.setIndexType("hnsw");
        pgVectorConfig.setDistanceType("cosine");
        pgVectorConfig.setHnswM(16);
        pgVectorConfig.setHnswEfConstruction(64);
        pgVectorConfig.setHnswEfSearch(100);
        pgVectorConfig.setAutoCreateTable(false); // 测试时不自动创建表
        pgVectorConfig.setBatchSize(100);

        vectorProviderConfig = new VectorProviderConfig();
        vectorProviderConfig.setProvider("pgvector");
        vectorProviderConfig.setEnabled(true);

        // 创建服务实例（不调用@PostConstruct）
        pgVectorService = new PgVectorServiceImpl(jdbcTemplate, pgVectorConfig, vectorProviderConfig);
    }

    @Test
    @DisplayName("测试提供者名称")
    void testProviderName() {
        assertEquals("pgvector", pgVectorService.getProviderName());
    }

    @Test
    @DisplayName("测试距离操作符配置")
    void testDistanceOperator() {
        // 测试余弦距离
        pgVectorConfig.setDistanceType("cosine");
        assertEquals("<=>", pgVectorConfig.getDistanceOperator());
        assertEquals("vector_cosine_ops", pgVectorConfig.getIndexOpsClass());

        // 测试L2距离
        pgVectorConfig.setDistanceType("l2");
        assertEquals("<->", pgVectorConfig.getDistanceOperator());
        assertEquals("vector_l2_ops", pgVectorConfig.getIndexOpsClass());

        // 测试内积
        pgVectorConfig.setDistanceType("inner_product");
        assertEquals("<#>", pgVectorConfig.getDistanceOperator());
        assertEquals("vector_ip_ops", pgVectorConfig.getIndexOpsClass());
    }

    @Test
    @DisplayName("测试插入向量")
    void testInsertVectors() {
        // Mock表存在检查
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"), 
                eq(Boolean.class), 
                eq("knowledge_vectors")))
                .thenReturn(true);

        // Mock批量插入
        when(jdbcTemplate.batchUpdate(anyString(), anyList()))
                .thenReturn(new int[]{1});

        // 准备测试数据
        float[] vector1 = new float[1024];
        Arrays.fill(vector1, 0.1f);
        
        List<float[]> vectors = List.of(vector1);
        List<Long> chunkIds = List.of(1L);
        List<Long> docIds = List.of(1L);
        List<String> contents = List.of("测试内容");
        List<String> categories = List.of("测试分类");

        // 执行插入
        List<String> vectorIds = pgVectorService.insertVectors(
                vectors, chunkIds, docIds, contents, categories);

        // 验证
        assertNotNull(vectorIds);
        assertEquals(1, vectorIds.size());
        assertFalse(vectorIds.get(0).isEmpty());
    }

    @Test
    @DisplayName("测试搜索向量")
    void testSearchVectors() {
        // Mock表存在检查
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Boolean.class),
                eq("knowledge_vectors")))
                .thenReturn(true);

        // Mock搜索结果
        VectorService.SearchResult mockResult = new VectorService.SearchResult();
        mockResult.setVectorId("test-uuid");
        mockResult.setChunkId(1L);
        mockResult.setDocId(1L);
        mockResult.setContent("测试内容");
        mockResult.setCategory("测试分类");
        mockResult.setScore(0.95f);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mockResult));

        // 准备查询向量
        float[] queryVector = new float[1024];
        Arrays.fill(queryVector, 0.1f);

        // 执行搜索
        List<VectorService.SearchResult> results = pgVectorService.search(queryVector, 5, 0.5f);

        // 验证
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("test-uuid", results.get(0).getVectorId());
        assertEquals(0.95f, results.get(0).getScore());
    }

    @Test
    @DisplayName("测试删除向量")
    void testDeleteVectors() {
        // Mock表存在检查
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Boolean.class),
                eq("knowledge_vectors")))
                .thenReturn(true);

        // Mock删除
        when(jdbcTemplate.update(contains("DELETE"), any(Object[].class)))
                .thenReturn(2);

        // 执行删除
        List<String> vectorIds = List.of("uuid-1", "uuid-2");
        pgVectorService.deleteVectors(vectorIds);

        // 验证调用
        verify(jdbcTemplate, times(1)).update(contains("DELETE"), any(Object[].class));
    }

    @Test
    @DisplayName("测试按文档ID删除")
    void testDeleteByDocId() {
        // Mock表存在检查
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Boolean.class),
                eq("knowledge_vectors")))
                .thenReturn(true);

        // Mock删除
        when(jdbcTemplate.update(contains("DELETE"), eq(123L)))
                .thenReturn(5);

        // 执行删除
        pgVectorService.deleteByDocId(123L);

        // 验证调用
        verify(jdbcTemplate, times(1)).update(contains("DELETE"), eq(123L));
    }

    @Test
    @DisplayName("测试获取向量数量")
    void testGetVectorCount() {
        // Mock表存在检查
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Boolean.class),
                eq("knowledge_vectors")))
                .thenReturn(true);

        // Mock计数
        when(jdbcTemplate.queryForObject(contains("COUNT"), eq(Long.class)))
                .thenReturn(100L);

        // 执行查询
        long count = pgVectorService.getVectorCount();

        // 验证
        assertEquals(100L, count);
    }

    @Test
    @DisplayName("测试服务不可用时的行为")
    void testServiceUnavailable() {
        // 设置为Milvus提供者
        vectorProviderConfig.setProvider("milvus");

        // 验证不可用
        assertFalse(pgVectorService.isAvailable());

        // 验证操作返回空结果
        float[] queryVector = new float[1024];
        List<VectorService.SearchResult> results = pgVectorService.search(queryVector, 5, 0.5f);
        assertTrue(results.isEmpty());

        List<String> insertResult = pgVectorService.insertVectors(
                List.of(queryVector), List.of(1L), List.of(1L), 
                List.of("test"), List.of("test"));
        assertTrue(insertResult.isEmpty());

        assertEquals(0, pgVectorService.getVectorCount());
    }

    @Test
    @DisplayName("测试提供者名称-验证")
    void testProviderNameValidation() {
        assertEquals("pgvector", pgVectorService.getProviderName());
    }
}
