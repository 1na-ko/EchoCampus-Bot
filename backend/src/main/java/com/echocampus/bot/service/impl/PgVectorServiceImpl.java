package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.PgVectorConfig;
import com.echocampus.bot.config.VectorProviderConfig;
import com.echocampus.bot.service.VectorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * pgvector向量数据库服务实现
 * 基于PostgreSQL的pgvector扩展实现向量存储和检索
 */
@Slf4j
@Service("pgVectorService")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vector.provider", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorServiceImpl implements VectorService {

    private final JdbcTemplate jdbcTemplate;
    private final PgVectorConfig pgVectorConfig;
    private final VectorProviderConfig vectorProviderConfig;

    private volatile boolean initialized = false;

    @PostConstruct
    public void init() {
        if (!vectorProviderConfig.isPgVector()) {
            log.info("当前向量存储提供者不是pgvector，跳过初始化");
            return;
        }
        
        if (pgVectorConfig.getAutoCreateTable()) {
            initVectorStore();
        }
    }

    @Override
    @Transactional
    public void initVectorStore() {
        try {
            log.info("开始初始化pgvector向量存储...");
            
            // 1. 创建pgvector扩展
            createExtension();
            
            // 2. 创建向量表
            createVectorTable();
            
            // 3. 创建索引
            createIndex();
            
            initialized = true;
            log.info("pgvector向量存储初始化完成");
        } catch (Exception e) {
            log.error("pgvector初始化失败: {}", e.getMessage(), e);
            initialized = false;
        }
    }

    /**
     * 创建pgvector扩展
     */
    private void createExtension() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            log.info("pgvector扩展已启用");
        } catch (Exception e) {
            log.warn("创建pgvector扩展时出现警告（可能已存在）: {}", e.getMessage());
        }
    }

    /**
     * 创建向量表
     */
    private void createVectorTable() {
        String tableName = pgVectorConfig.getTableName();
        int dimension = pgVectorConfig.getDimension();
        
        // 检查表是否存在
        String checkSql = """
            SELECT EXISTS (
                SELECT FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_name = ?
            )
            """;
        
        Boolean exists = jdbcTemplate.queryForObject(checkSql, Boolean.class, tableName);
        
        if (Boolean.TRUE.equals(exists)) {
            log.info("向量表已存在: {}", tableName);
            // 检查维度是否匹配
            checkAndUpdateDimension(tableName, dimension);
            return;
        }
        
        // 创建向量表
        String createTableSql = String.format("""
            CREATE TABLE %s (
                id VARCHAR(100) PRIMARY KEY,
                vector vector(%d) NOT NULL,
                chunk_id BIGINT NOT NULL,
                doc_id BIGINT NOT NULL,
                content TEXT,
                category VARCHAR(100),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                
                -- 外键约束（可选，提高数据一致性）
                CONSTRAINT fk_chunk FOREIGN KEY (chunk_id) REFERENCES knowledge_chunks(id) ON DELETE CASCADE,
                CONSTRAINT fk_doc FOREIGN KEY (doc_id) REFERENCES knowledge_docs(id) ON DELETE CASCADE
            )
            """, tableName, dimension);
        
        try {
            jdbcTemplate.execute(createTableSql);
            log.info("向量表创建成功: {}, 维度: {}", tableName, dimension);
        } catch (Exception e) {
            // 如果外键约束失败，尝试不带外键创建
            log.warn("带外键创建表失败，尝试不带外键创建: {}", e.getMessage());
            String createTableWithoutFkSql = String.format("""
                CREATE TABLE %s (
                    id VARCHAR(100) PRIMARY KEY,
                    vector vector(%d) NOT NULL,
                    chunk_id BIGINT NOT NULL,
                    doc_id BIGINT NOT NULL,
                    content TEXT,
                    category VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """, tableName, dimension);
            jdbcTemplate.execute(createTableWithoutFkSql);
            log.info("向量表创建成功（无外键）: {}, 维度: {}", tableName, dimension);
        }
        
        // 创建辅助索引
        createAuxiliaryIndexes(tableName);
    }

    /**
     * 检查并更新维度
     */
    private void checkAndUpdateDimension(String tableName, int expectedDimension) {
        try {
            // 获取当前向量列的维度
            String dimensionSql = """
                SELECT atttypmod 
                FROM pg_attribute 
                WHERE attrelid = ?::regclass 
                AND attname = 'vector'
                """;
            
            Integer currentDimension = jdbcTemplate.queryForObject(dimensionSql, Integer.class, tableName);
            
            if (currentDimension != null && currentDimension != expectedDimension) {
                log.warn("向量维度不匹配! 当前: {}, 期望: {}. 需要手动处理数据迁移。", 
                        currentDimension, expectedDimension);
            }
        } catch (Exception e) {
            log.debug("检查维度时出现异常: {}", e.getMessage());
        }
    }

    /**
     * 创建辅助索引
     */
    private void createAuxiliaryIndexes(String tableName) {
        try {
            // chunk_id索引
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_chunk_id ON %s(chunk_id)", 
                    tableName, tableName));
            
            // doc_id索引
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_doc_id ON %s(doc_id)", 
                    tableName, tableName));
            
            // category索引
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS idx_%s_category ON %s(category)", 
                    tableName, tableName));
            
            log.info("辅助索引创建成功");
        } catch (Exception e) {
            log.warn("创建辅助索引时出现警告: {}", e.getMessage());
        }
    }

    /**
     * 创建向量索引
     */
    private void createIndex() {
        String tableName = pgVectorConfig.getTableName();
        String indexType = pgVectorConfig.getIndexType().toLowerCase();
        String opsClass = pgVectorConfig.getIndexOpsClass();
        String indexName = String.format("idx_%s_vector_%s", tableName, indexType);
        
        // 检查索引是否存在
        String checkIndexSql = """
            SELECT EXISTS (
                SELECT FROM pg_indexes 
                WHERE tablename = ? 
                AND indexname = ?
            )
            """;
        
        Boolean indexExists = jdbcTemplate.queryForObject(checkIndexSql, Boolean.class, tableName, indexName);
        
        if (Boolean.TRUE.equals(indexExists)) {
            log.info("向量索引已存在: {}", indexName);
            return;
        }
        
        String createIndexSql;
        if ("hnsw".equals(indexType)) {
            createIndexSql = String.format("""
                CREATE INDEX %s ON %s 
                USING hnsw (vector %s)
                WITH (m = %d, ef_construction = %d)
                """, 
                indexName, tableName, opsClass,
                pgVectorConfig.getHnswM(), 
                pgVectorConfig.getHnswEfConstruction());
        } else {
            // IVFFlat索引
            createIndexSql = String.format("""
                CREATE INDEX %s ON %s 
                USING ivfflat (vector %s)
                WITH (lists = %d)
                """, 
                indexName, tableName, opsClass,
                pgVectorConfig.getIvfflatLists());
        }
        
        try {
            jdbcTemplate.execute(createIndexSql);
            log.info("向量索引创建成功: {} (类型: {})", indexName, indexType);
        } catch (Exception e) {
            log.error("创建向量索引失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public List<String> insertVectors(List<float[]> vectors, List<Long> chunkIds, List<Long> docIds,
                                       List<String> contents, List<String> categories) {
        if (!isAvailable() || vectors.isEmpty()) {
            log.warn("pgvector服务不可用或向量列表为空");
            return Collections.emptyList();
        }

        String tableName = pgVectorConfig.getTableName();
        List<String> vectorIds = new ArrayList<>();
        
        // 生成向量ID
        for (int i = 0; i < vectors.size(); i++) {
            vectorIds.add(UUID.randomUUID().toString());
        }

        String insertSql = String.format("""
            INSERT INTO %s (id, vector, chunk_id, doc_id, content, category)
            VALUES (?, ?::vector, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                vector = EXCLUDED.vector,
                chunk_id = EXCLUDED.chunk_id,
                doc_id = EXCLUDED.doc_id,
                content = EXCLUDED.content,
                category = EXCLUDED.category
            """, tableName);

        int batchSize = pgVectorConfig.getBatchSize();
        int totalInserted = 0;
        
        for (int i = 0; i < vectors.size(); i += batchSize) {
            int end = Math.min(i + batchSize, vectors.size());
            List<Object[]> batchArgs = new ArrayList<>();
            
            for (int j = i; j < end; j++) {
                String vectorString = arrayToVectorString(vectors.get(j));
                batchArgs.add(new Object[]{
                        vectorIds.get(j),
                        vectorString,
                        chunkIds.get(j),
                        docIds.get(j),
                        contents.get(j),
                        categories.get(j)
                });
            }
            
            jdbcTemplate.batchUpdate(insertSql, batchArgs);
            totalInserted += batchArgs.size();
            
            if (totalInserted % 100 == 0) {
                log.debug("已插入 {} 条向量", totalInserted);
            }
        }

        log.info("成功插入 {} 条向量到pgvector", totalInserted);
        return vectorIds;
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK, float threshold) {
        if (!isAvailable()) {
            log.warn("pgvector服务不可用");
            return Collections.emptyList();
        }

        String tableName = pgVectorConfig.getTableName();
        String distanceOperator = pgVectorConfig.getDistanceOperator();
        String vectorString = arrayToVectorString(queryVector);
        
        // 设置HNSW搜索参数（如果使用HNSW索引）
        if ("hnsw".equalsIgnoreCase(pgVectorConfig.getIndexType())) {
            jdbcTemplate.execute(String.format(
                    "SET hnsw.ef_search = %d", 
                    pgVectorConfig.getHnswEfSearch()));
        } else {
            jdbcTemplate.execute(String.format(
                    "SET ivfflat.probes = %d", 
                    pgVectorConfig.getIvfflatProbes()));
        }

        // 构建搜索SQL
        // 注意：pgvector的距离越小越相似，需要转换为相似度分数
        String searchSql;
        if ("cosine".equalsIgnoreCase(pgVectorConfig.getDistanceType())) {
            // 余弦距离转相似度: similarity = 1 - distance
            searchSql = String.format("""
                SELECT id, chunk_id, doc_id, content, category,
                       (1 - (vector %s ?::vector)) AS similarity
                FROM %s
                WHERE (1 - (vector %s ?::vector)) >= ?
                ORDER BY vector %s ?::vector
                LIMIT ?
                """, distanceOperator, tableName, distanceOperator, distanceOperator);
        } else {
            // 其他距离类型使用负距离作为分数
            searchSql = String.format("""
                SELECT id, chunk_id, doc_id, content, category,
                       (1.0 / (1.0 + (vector %s ?::vector))) AS similarity
                FROM %s
                WHERE (1.0 / (1.0 + (vector %s ?::vector))) >= ?
                ORDER BY vector %s ?::vector
                LIMIT ?
                """, distanceOperator, tableName, distanceOperator, distanceOperator);
        }

        try {
            List<SearchResult> results = jdbcTemplate.query(
                    searchSql,
                    new SearchResultRowMapper(),
                    vectorString, vectorString, threshold, vectorString, topK
            );
            
            log.info("pgvector搜索完成，返回 {} 条结果", results.size());
            return results;
        } catch (Exception e) {
            log.error("pgvector搜索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public void deleteVectors(List<String> vectorIds) {
        if (!isAvailable() || vectorIds.isEmpty()) {
            return;
        }

        String tableName = pgVectorConfig.getTableName();
        String placeholders = vectorIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));
        
        String deleteSql = String.format("DELETE FROM %s WHERE id IN (%s)", tableName, placeholders);
        
        int deleted = jdbcTemplate.update(deleteSql, vectorIds.toArray());
        log.info("成功删除 {} 条向量", deleted);
    }

    @Override
    @Transactional
    public void deleteByDocId(Long docId) {
        if (!isAvailable()) {
            return;
        }

        String tableName = pgVectorConfig.getTableName();
        String deleteSql = String.format("DELETE FROM %s WHERE doc_id = ?", tableName);
        
        int deleted = jdbcTemplate.update(deleteSql, docId);
        log.info("成功删除文档 {} 的 {} 条向量", docId, deleted);
    }

    @Override
    public long getVectorCount() {
        if (!isAvailable()) {
            return 0;
        }

        String tableName = pgVectorConfig.getTableName();
        String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
        
        try {
            Long count = jdbcTemplate.queryForObject(countSql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取向量数量失败: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isAvailable() {
        if (!vectorProviderConfig.isPgVector()) {
            return false;
        }
        
        if (!initialized) {
            // 尝试检查表是否存在
            try {
                String checkSql = """
                    SELECT EXISTS (
                        SELECT FROM information_schema.tables 
                        WHERE table_schema = 'public' 
                        AND table_name = ?
                    )
                    """;
                Boolean exists = jdbcTemplate.queryForObject(checkSql, Boolean.class, 
                        pgVectorConfig.getTableName());
                initialized = Boolean.TRUE.equals(exists);
            } catch (Exception e) {
                log.debug("检查pgvector可用性失败: {}", e.getMessage());
                return false;
            }
        }
        
        return initialized;
    }

    @Override
    public String getProviderName() {
        return "pgvector";
    }

    /**
     * 将float数组转换为pgvector字符串格式
     */
    private String arrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 搜索结果行映射器
     */
    private static class SearchResultRowMapper implements RowMapper<SearchResult> {
        @Override
        public SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            SearchResult result = new SearchResult();
            result.setVectorId(rs.getString("id"));
            result.setChunkId(rs.getLong("chunk_id"));
            result.setDocId(rs.getLong("doc_id"));
            result.setContent(rs.getString("content"));
            result.setCategory(rs.getString("category"));
            result.setScore(rs.getFloat("similarity"));
            return result;
        }
    }
}
