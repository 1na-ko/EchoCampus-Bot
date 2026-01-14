-- ============================================
-- PostgreSQL + pgvector 初始化脚本
-- 在容器首次启动时自动执行
-- ============================================

-- 启用pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展是否安装成功
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        RAISE EXCEPTION 'pgvector extension is not installed!';
    END IF;
    RAISE NOTICE 'pgvector extension is ready!';
END $$;

-- ============================================
-- 向量存储表 (knowledge_vectors)
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_vectors (
    id VARCHAR(100) PRIMARY KEY,
    vector vector(1024) NOT NULL,
    chunk_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    content TEXT,
    category VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建辅助索引
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_chunk_id ON knowledge_vectors(chunk_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_doc_id ON knowledge_vectors(doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_category ON knowledge_vectors(category);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_created_at ON knowledge_vectors(created_at);

-- 创建HNSW向量索引（余弦相似度）
-- 注意：索引创建可能需要一些时间，取决于数据量
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_vector_hnsw 
ON knowledge_vectors USING hnsw (vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 添加表注释
COMMENT ON TABLE knowledge_vectors IS '知识库向量存储表 - pgvector实现';
COMMENT ON COLUMN knowledge_vectors.id IS '向量唯一标识(UUID)';
COMMENT ON COLUMN knowledge_vectors.vector IS '1024维向量数据';
COMMENT ON COLUMN knowledge_vectors.chunk_id IS '关联的知识片段ID';
COMMENT ON COLUMN knowledge_vectors.doc_id IS '关联的文档ID';
COMMENT ON COLUMN knowledge_vectors.content IS '原始文本内容（冗余存储，用于调试）';
COMMENT ON COLUMN knowledge_vectors.category IS '知识分类';
COMMENT ON COLUMN knowledge_vectors.created_at IS '创建时间';

-- ============================================
-- 辅助函数：余弦相似度搜索
-- ============================================
CREATE OR REPLACE FUNCTION search_similar_vectors(
    query_vector vector(1024),
    top_k INTEGER DEFAULT 10,
    similarity_threshold FLOAT DEFAULT 0.0
)
RETURNS TABLE (
    id VARCHAR(100),
    chunk_id BIGINT,
    doc_id BIGINT,
    content TEXT,
    category VARCHAR(100),
    similarity FLOAT
) AS $$
BEGIN
    -- 设置HNSW搜索参数
    SET hnsw.ef_search = 100;
    
    RETURN QUERY
    SELECT 
        kv.id,
        kv.chunk_id,
        kv.doc_id,
        kv.content,
        kv.category,
        (1 - (kv.vector <=> query_vector))::FLOAT AS similarity
    FROM knowledge_vectors kv
    WHERE (1 - (kv.vector <=> query_vector)) >= similarity_threshold
    ORDER BY kv.vector <=> query_vector
    LIMIT top_k;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION search_similar_vectors IS '基于余弦相似度的向量搜索函数';

-- ============================================
-- 辅助函数：统计向量数据
-- ============================================
CREATE OR REPLACE FUNCTION get_vector_statistics()
RETURNS TABLE (
    total_vectors BIGINT,
    unique_docs BIGINT,
    unique_chunks BIGINT,
    categories_count BIGINT,
    avg_content_length FLOAT,
    oldest_record TIMESTAMP,
    newest_record TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::BIGINT AS total_vectors,
        COUNT(DISTINCT kv.doc_id)::BIGINT AS unique_docs,
        COUNT(DISTINCT kv.chunk_id)::BIGINT AS unique_chunks,
        COUNT(DISTINCT kv.category)::BIGINT AS categories_count,
        AVG(LENGTH(kv.content))::FLOAT AS avg_content_length,
        MIN(kv.created_at) AS oldest_record,
        MAX(kv.created_at) AS newest_record
    FROM knowledge_vectors kv;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_vector_statistics IS '获取向量存储统计信息';

-- ============================================
-- 初始化完成提示
-- ============================================
DO $$
BEGIN
    RAISE NOTICE '====================================';
    RAISE NOTICE 'pgvector initialization completed!';
    RAISE NOTICE 'Vector dimension: 1024';
    RAISE NOTICE 'Index type: HNSW';
    RAISE NOTICE 'Distance metric: Cosine';
    RAISE NOTICE '====================================';
END $$;
