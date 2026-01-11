-- ============================================
-- EchoCampus 校园问答机器人 - PostgreSQL数据库设计
-- ============================================
-- 注意：数据库已由Docker环境变量自动创建，此处直接使用

-- ============================================
-- 1. 用户表 (users)
-- ============================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    nickname VARCHAR(50),
    role VARCHAR(20) DEFAULT 'USER',  -- USER, ADMIN
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, LOCKED
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO users (username, password, email, nickname, role, status) VALUES 
('admin', '$2a$10$7JB720yubVSZFyL9BbGfQe1C6PQRJZG6fGS.0FdJMR5N1h6W5GPTG', 'admin@example.com', 'Admin', 'ADMIN', 'ACTIVE');

-- ============================================
-- 2. 邮箱验证码表 (email_verification_codes)
-- ============================================
CREATE TABLE IF NOT EXISTS email_verification_codes (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    code VARCHAR(10) NOT NULL,
    type VARCHAR(20) NOT NULL,  -- REGISTER, RESET_PASSWORD, etc.
    expired_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_email_verification_codes_email ON email_verification_codes(email);
CREATE INDEX idx_email_verification_codes_type ON email_verification_codes(type);
CREATE INDEX idx_email_verification_codes_expired_at ON email_verification_codes(expired_at);
CREATE INDEX idx_email_verification_codes_used ON email_verification_codes(used);
CREATE INDEX idx_email_verification_codes_email_type_used ON email_verification_codes(email, type, used);

-- 创建复合索引用于查询未使用的验证码
CREATE INDEX idx_email_verification_codes_query ON email_verification_codes(email, type, used, expired_at DESC);

-- 添加注释
COMMENT ON TABLE email_verification_codes IS '邮箱验证码表';
COMMENT ON COLUMN email_verification_codes.email IS '邮箱地址';
COMMENT ON COLUMN email_verification_codes.code IS '验证码';
COMMENT ON COLUMN email_verification_codes.type IS '验证码类型（REGISTER-注册，RESET_PASSWORD-重置密码等）';
COMMENT ON COLUMN email_verification_codes.expired_at IS '过期时间';
COMMENT ON COLUMN email_verification_codes.used IS '是否已使用';
COMMENT ON COLUMN email_verification_codes.used_at IS '使用时间';
COMMENT ON COLUMN email_verification_codes.ip_address IS '请求IP地址';
COMMENT ON COLUMN email_verification_codes.created_at IS '创建时间';
COMMENT ON COLUMN email_verification_codes.updated_at IS '更新时间';

-- ============================================
-- 3. 对话会话表 (conversations)
-- ============================================
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, ARCHIVED, DELETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_conversations_status ON conversations(status);
CREATE INDEX idx_conversations_updated_at ON conversations(updated_at DESC);

-- ============================================
-- 4. 对话消息表 (messages)
-- ============================================
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    parent_message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    sender_type VARCHAR(20) NOT NULL,  -- USER, BOT, SYSTEM
    content TEXT NOT NULL,
    token_count INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}',  -- 存储额外信息(检索文档、耗时等)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_sender_type ON messages(sender_type);
CREATE INDEX idx_messages_created_at ON messages(created_at);

-- 添加消息后自动更新会话的消息数量和更新时间
CREATE OR REPLACE FUNCTION update_conversation_stats()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE conversations 
    SET message_count = message_count + 1,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_conversation_stats
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_conversation_stats();

-- ============================================
-- 5. 知识库文档表 (knowledge_docs)
-- ============================================
CREATE TABLE knowledge_docs (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),  -- pdf, txt, md, docx, doc, ppt, pptx
    category VARCHAR(100),  -- 课程简介、实验室介绍、常见问题
    tags VARCHAR(500),  -- 标签,逗号分隔
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, PROCESSING, FAILED
    vector_count INTEGER DEFAULT 0,  -- 关联的向量数量
    process_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    process_message TEXT,  -- 处理结果信息
    last_indexed_at TIMESTAMP,  -- 最后索引时间
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_knowledge_docs_category ON knowledge_docs(category);
CREATE INDEX idx_knowledge_docs_status ON knowledge_docs(status);
CREATE INDEX idx_knowledge_docs_file_type ON knowledge_docs(file_type);
CREATE INDEX idx_knowledge_docs_created_by ON knowledge_docs(created_by);
CREATE INDEX idx_knowledge_docs_created_at ON knowledge_docs(created_at DESC);

-- ============================================
-- 6. 知识库文档片段表 (knowledge_chunks)
-- ============================================
CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES knowledge_docs(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,  -- 片段在文档中的位置
    chunk_type VARCHAR(20) DEFAULT 'TEXT',  -- TEXT, TITLE, CODE
    content TEXT NOT NULL,  -- 原始文本内容
    content_hash VARCHAR(64),  -- 内容哈希,用于去重
    vector_id VARCHAR(100),  -- Milvus中的向量ID
    page_number INTEGER,  -- PDF页码
    metadata JSONB DEFAULT '{}',  -- 存储额外信息(段落标题、关键词等)
    token_count INTEGER DEFAULT 0,
    start_position INTEGER,  -- 在文档中的起始位置
    end_position INTEGER,  -- 在文档中的结束位置
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_knowledge_chunks_doc_id ON knowledge_chunks(doc_id);
CREATE INDEX idx_knowledge_chunks_vector_id ON knowledge_chunks(vector_id);
CREATE INDEX idx_knowledge_chunks_content_hash ON knowledge_chunks(content_hash);

-- ============================================
-- 7. 检索日志表 (search_logs)
-- ============================================
CREATE TABLE search_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    conversation_id BIGINT REFERENCES conversations(id),
    query TEXT NOT NULL,  -- 用户查询
    query_vector_id VARCHAR(100),  -- 查询向量ID
    retrieved_chunks JSONB,  -- 检索到的片段信息
    response_time_ms INTEGER,  -- 响应时间(毫秒)
    answer_tokens INTEGER,  -- 答案token数
    status VARCHAR(20) DEFAULT 'SUCCESS',  -- SUCCESS, FAILED
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_search_logs_user_id ON search_logs(user_id);
CREATE INDEX idx_search_logs_conversation_id ON search_logs(conversation_id);
CREATE INDEX idx_search_logs_created_at ON search_logs(created_at);

-- ============================================
-- 8. 系统配置表 (system_config)
-- ============================================
CREATE TABLE system_config (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(20) DEFAULT 'STRING',  -- STRING, NUMBER, BOOLEAN, JSON
    description VARCHAR(500),
    is_editable BOOLEAN DEFAULT TRUE,
    updated_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认配置
INSERT INTO system_config (config_key, config_value, config_type, description) VALUES 
('rag.top_k', '5', 'NUMBER', 'RAG检索返回的最相关文档数量'),
('rag.temperature', '0.7', 'NUMBER', 'AI生成答案的温度参数(0.0-1.0)'),
('rag.max_tokens', '1000', 'NUMBER', 'AI生成答案的最大token数'),
('rag.similarity_threshold', '0.7', 'NUMBER', '相似度阈值,低于此值的结果将被过滤'),
('milvus.collection_name', 'echocampus_knowledge', 'STRING', 'Milvus向量集合名称'),
('milvus.dimension', '1536', 'NUMBER', '向量维度(根据Qwen3-Embedding模型)'),
('milvus.metric_type', 'COSINE', 'STRING', '相似度度量类型(L2, IP, COSINE)'),
('milvus.index_type', 'IVF_FLAT', 'STRING', '索引类型(IVF_FLAT, HNSW等)'),
('milvus.nprobe', '10', 'NUMBER', '搜索的簇数量'),
('embedding.model', 'text-embedding-v3', 'STRING', 'Embedding模型(阿里云百炼平台text-embedding-v3)'),
('embedding.api_url', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'STRING', 'Embedding API地址'),
('embedding.batch_size', '10', 'NUMBER', 'Embedding批量处理大小'),
('embedding.max_retries', '3', 'NUMBER', 'Embedding API最大重试次数'),
('llm.model', 'deepseek-v3.2', 'STRING', 'LLM模型(DeepSeek V3.2)'),
('llm.api_url', 'https://api.deepseek.com/v1/chat/completions', 'STRING', 'LLM API地址'),
('llm.max_tokens', '1000', 'NUMBER', 'LLM生成最大token数'),
('llm.timeout', '30', 'NUMBER', 'LLM API超时时间(秒)'),
('file.upload.max_size', '10485760', 'NUMBER', '文件上传最大大小(字节,10MB)'),
('file.allowed_types', 'pdf,txt,md,docx,doc,ppt,pptx', 'STRING', '允许上传的文件类型'),
('chunking.strategy', 'recursive', 'STRING', '文本切块策略(recursive/paragraph/line/character)'),
('chunking.max_size', '500', 'NUMBER', '文本切块最大字符数'),
('chunking.overlap_size', '50', 'NUMBER', '文本切块重叠字符数'),
('chunking.min_chunk_size', '1', 'NUMBER', '文本切块最小字符数'),
('chunking.separators', '\n\n,\n,。,！,？,.,!,?, ,', 'STRING', '递归分割的分隔符(逗号分隔)'),
('system.name', 'EchoCampus-Bot', 'STRING', '系统名称'),
('system.description', '基于RAG技术的智能校园问答系统', 'STRING', '系统描述');

-- ============================================
-- 9. 操作日志表 (operation_logs)
-- ============================================
CREATE TABLE operation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    operation_type VARCHAR(50) NOT NULL,  -- LOGIN, UPLOAD, DELETE, UPDATE
    operation_desc TEXT,
    resource_type VARCHAR(50),  -- USER, DOC, CHUNK, CONFIG
    resource_id BIGINT,
    ip_address INET,
    user_agent TEXT,
    request_params JSONB,
    response_result JSONB,
    status VARCHAR(20) DEFAULT 'SUCCESS',  -- SUCCESS, FAILED
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_operation_logs_user_id ON operation_logs(user_id);
CREATE INDEX idx_operation_logs_operation_type ON operation_logs(operation_type);
CREATE INDEX idx_operation_logs_created_at ON operation_logs(created_at);

-- ============================================
-- 10. 知识库分类表 (knowledge_categories)
-- ============================================
CREATE TABLE knowledge_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id BIGINT REFERENCES knowledge_categories(id),
    sort_order INTEGER DEFAULT 0,
    doc_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_knowledge_categories_parent_id ON knowledge_categories(parent_id);
CREATE INDEX idx_knowledge_categories_sort_order ON knowledge_categories(sort_order);

-- 注意：知识库分类由用户根据实际需求创建，此处不预设分类数据

-- ============================================
-- 11. 系统统计表 (system_statistics)
-- ============================================
CREATE TABLE system_statistics (
    id BIGSERIAL PRIMARY KEY,
    stat_date DATE NOT NULL,
    stat_type VARCHAR(50) NOT NULL,  -- DAILY, WEEKLY, MONTHLY
    user_count INTEGER DEFAULT 0,
    conversation_count INTEGER DEFAULT 0,
    message_count INTEGER DEFAULT 0,
    doc_count INTEGER DEFAULT 0,
    query_count INTEGER DEFAULT 0,
    avg_response_time_ms INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建唯一索引,每天一条记录
CREATE UNIQUE INDEX idx_system_statistics_date_type ON system_statistics(stat_date, stat_type);

-- ============================================
-- 视图和函数
-- ============================================

-- 创建视图: 知识库统计
CREATE VIEW knowledge_stats AS
SELECT 
    kc.id AS category_id,
    kc.name AS category_name,
    COUNT(kd.id) AS doc_count,
    SUM(kd.vector_count) AS total_vectors,
    MAX(kd.updated_at) AS last_updated
FROM knowledge_categories kc
LEFT JOIN knowledge_docs kd ON kc.id = kd.category::bigint
WHERE kd.status = 'ACTIVE' OR kd.status IS NULL
GROUP BY kc.id, kc.name
ORDER BY kc.sort_order;

-- 创建视图: 用户活跃度统计
CREATE VIEW user_activity_stats AS
SELECT 
    u.id AS user_id,
    u.username,
    u.nickname,
    COUNT(DISTINCT c.id) AS conversation_count,
    COUNT(DISTINCT m.id) AS message_count,
    MAX(m.created_at) AS last_activity
FROM users u
LEFT JOIN conversations c ON u.id = c.user_id AND c.status = 'ACTIVE'
LEFT JOIN messages m ON c.id = m.conversation_id
WHERE u.status = 'ACTIVE'
GROUP BY u.id, u.username, u.nickname;

-- 创建函数: 更新知识库文档统计
CREATE OR REPLACE FUNCTION update_doc_vector_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE knowledge_docs 
        SET vector_count = vector_count + 1 
        WHERE id = NEW.doc_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE knowledge_docs 
        SET vector_count = vector_count - 1 
        WHERE id = OLD.doc_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trigger_update_doc_vector_count
    AFTER INSERT OR DELETE ON knowledge_chunks
    FOR EACH ROW
    EXECUTE FUNCTION update_doc_vector_count();

-- ============================================
-- 性能优化索引
-- ============================================

-- 为全文搜索创建索引
CREATE INDEX idx_knowledge_docs_title_search ON knowledge_docs USING gin(to_tsvector('english', title));
CREATE INDEX idx_knowledge_docs_description_search ON knowledge_docs USING gin(to_tsvector('english', description));
CREATE INDEX idx_knowledge_chunks_content_search ON knowledge_chunks USING gin(to_tsvector('english', content));

-- 为JSONB字段创建索引
CREATE INDEX idx_messages_metadata ON messages USING gin(metadata);
CREATE INDEX idx_knowledge_chunks_metadata ON knowledge_chunks USING gin(metadata);
CREATE INDEX idx_search_logs_retrieved_chunks ON search_logs USING gin(retrieved_chunks);

-- ============================================
-- 备注
-- ============================================
