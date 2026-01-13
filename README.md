# 智能校园/IT知识问答机器人 - 项目结构设计文档

## 📋 项目概述

本项目是一个基于**RAG(Retrieval-Augmented Generation)**技术的智能校园/IT知识问答机器人,采用前后端分离架构,结合Spring Boot、Vue.js、PostgreSQL、Milvus等现代化技术栈实现。

### 核心功能
- 💬 智能问答: 基于RAG技术提供准确的校园知识问答
- 📚 知识库管理: 支持文档上传、分类、检索和向量化
- 💾 对话历史: 支持多轮对话,保存对话记录
- ⚙️ 系统配置: 灵活的系统参数配置
- 📊 数据统计: 问答统计和系统监控

### 支持的文档格式
- **PDF** (.pdf) - 使用Apache PDFBox解析
- **TXT** (.txt) - 使用Java原生API解析
- **Markdown** (.md) - 使用Flexmark解析
- **Word** (.docx, .doc) - 使用Apache POI解析
- **PowerPoint** (.pptx, .ppt) - 使用Apache POI解析

### 技术亮点
- ✨ 使用**LangChain4j**进行智能文本切块(递归分割、语义保持)
- ✨ 支持灵活的chunking策略配置
- ✨ 根据文档类型自动选择最优解析和分割策略
- ✨ 完整的文档解析器工厂模式实现

## 1. 系统整体架构

### 1.1 架构概述

本系统采用经典的**前后端分离**架构,结合**RAG(Retrieval-Augmented Generation)**技术实现智能问答功能。

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端 (Vue.js) │    │  后端 (Spring   │    │   AI 服务层    │
│                 │────│      Boot)     │────│                 │
│  • 聊天界面      │    │                │    │  • 阿里云       │
│  • 知识库管理    │    │  • RESTful API │    │    Embedding   │
│  • 历史记录      │    │  • 业务逻辑    │    │  • DeepSeek    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                │
              ┌─────────────────┴─────────────────┐
              │        数据存储层                  │
              │                                   │
              │  ┌─────────────────┐  ┌─────────────────┐
              │  │   PostgreSQL    │  │     Milvus      │
              │  │                 │  │                 │
              │  │  • 对话历史      │  │  • 知识向量    │
              │  │  • 用户信息      │  │  • 检索索引    │
              │  │  • 知识库元数据  │  │                 │
              │  └─────────────────┘  └─────────────────┘
              └───────────────────────────────────────────────┘
```

### 1.2 技术栈分层

#### 前端层 (Presentation Layer)
- **框架**: Vue.js 3.4.0 + TypeScript 5.3.3
- **UI组件**: Ant Design Vue 4.1.1
- **状态管理**: Pinia 2.1.7
- **HTTP客户端**: Axios 1.6.5
- **特色功能**: 响应式设计、Markdown渲染、代码高亮

#### 后端层 (Business Logic Layer)
- **框架**: Spring Boot 3.2.1
- **ORM框架**: MyBatis-Plus 3.5.5
- **数据库连接池**: Druid 1.2.20
- **API文档**: Knife4j 4.4.0 (基于Swagger/OpenAPI)
- **安全框架**: Spring Security (JWT认证)
- **邮件服务**: Spring Boot Starter Mail
- **依赖管理**: Maven
- **文档解析**: LangChain4j 0.28.0 + Apache POI 5.2.5 + Apache PDFBox 3.0.1
    - LangChain4j: 智能文本切块(递归分割、语义保持)
    - LangChain4j OpenAI: OpenAI兼容接口支持
    - Apache PDFBox: PDF文档解析
    - Apache POI: Word/PowerPoint/Excel文档解析
    - Flexmark: Markdown文档解析
    - Jsoup: HTML解析
- **工具库**:
    - Apache Commons Lang3: 字符串工具
    - Apache Commons IO: 文件操作工具
    - Lombok: 简化Java代码
    - Hutool: Java工具类库
    - OkHttp: HTTP客户端

#### 数据存储层 (Data Layer)
- **关系型数据库**: PostgreSQL 15
    - 存储用户信息、对话历史、知识库元数据
- **向量数据库**: Milvus v2.3.4
    - 存储知识库文档的向量化表示
    - 支持高效的相似度检索

#### AI服务层 (AI Service Layer)
- **文本嵌入**: 阿里云百炼平台 Qwen3-Embedding (text-embedding-v3)
  - 将文本转换为1024维高维向量表示
  - API地址: https://dashscope.aliyuncs.com/compatible-mode/v1
- **大语言模型**: DeepSeek API (deepseek-chat)
  - 基于检索内容生成自然语言答案
  - API地址: https://api.deepseek.com/v1/chat/completions

### 1.3 核心工作流程

#### RAG问答流程
```
用户提问
    ↓
[前端] 发送问题到后端
    ↓
[后端] 问题预处理(清洗、规范化)
    ↓
[阿里云Qwen3-Embedding] 将问题转换为1024维向量
    ↓
[Milvus] 向量相似度检索,获取Top-K相关文档
    ↓
[后端] 构建Prompt(系统提示 + 检索文档 + 用户问题)
    ↓
[DeepSeek API] 生成答案
    ↓
[后端] 后处理(格式化、过滤)
    ↓
[PostgreSQL] 保存对话历史
    ↓
[前端] 展示答案
```

#### 知识库管理流程
```
管理员上传文档
    ↓
[后端] 文档解析(支持PDF、TXT、MD、DOCX、PPT、PPTX等格式)
    ↓
[LangChain4j] 智能文本切块(递归分割、语义保持)
    ↓
[阿里云Qwen3-Embedding] 文本块向量化(1024维)
    ↓
[Milvus] 存储向量 + [PostgreSQL] 存储元数据
    ↓
返回操作结果
```

## 2. 数据库设计

### 2.1 PostgreSQL 数据库设计

#### 2.1.1 用户表 (users)
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    role VARCHAR(20) DEFAULT 'USER',  -- USER, ADMIN
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 2.1.2 对话会话表 (conversations)
```sql
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE'  -- ACTIVE, ARCHIVED, DELETED
);
```

#### 2.1.3 对话消息表 (messages)
```sql
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id),
    sender_type VARCHAR(20) NOT NULL,  -- USER, BOT, SYSTEM
    content TEXT NOT NULL,
    metadata JSONB,  -- 存储额外信息(检索文档、耗时等)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);
```

#### 2.1.4 知识库文档表 (knowledge_docs)
```sql
CREATE TABLE knowledge_docs (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),  -- pdf, txt, md, docx, doc, ppt, pptx
    category VARCHAR(100),  -- 课程简介、实验室介绍、常见问题
    status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, DELETED
    vector_count INTEGER DEFAULT 0,  -- 关联的向量数量
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_knowledge_docs_category ON knowledge_docs(category);
CREATE INDEX idx_knowledge_docs_status ON knowledge_docs(status);
```

#### 2.1.5 知识库片段表 (knowledge_chunks)
```sql
CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT REFERENCES knowledge_docs(id),
    chunk_index INTEGER NOT NULL,  -- 片段在文档中的位置
    content TEXT NOT NULL,  -- 原始文本内容
    content_hash VARCHAR(64),  -- 内容哈希,用于去重
    vector_id VARCHAR(100),  -- Milvus中的向量ID
    metadata JSONB,  -- 存储额外信息(段落标题、关键词等)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_knowledge_chunks_doc_id ON knowledge_chunks(doc_id);
CREATE INDEX idx_knowledge_chunks_vector_id ON knowledge_chunks(vector_id);
```

#### 2.1.6 系统配置表 (system_config)
```sql
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

-- 初始化数据
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
```

#### 2.1.7 邮箱验证码表 (email_verification_codes)
```sql
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
CREATE INDEX idx_email_verification_codes_query ON email_verification_codes(email, type, used, expired_at DESC);
```

#### 2.1.8 检索日志表 (search_logs)
```sql
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
```

#### 2.1.9 操作日志表 (operation_logs)
```sql
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
```

#### 2.1.10 知识库分类表 (knowledge_categories)
```sql
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
```

#### 2.1.11 系统统计表 (system_statistics)
```sql
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stat_date, stat_type)
);

-- 创建索引
CREATE INDEX idx_system_statistics_stat_date ON system_statistics(stat_date);
CREATE INDEX idx_system_statistics_stat_type ON system_statistics(stat_type);
```

### 2.2 Milvus 向量数据库设计

#### 2.2.1 集合设计
```python
# Milvus集合配置
{
    "collection_name": "it_knowledge",
    "description": "IT知识问答系统的知识库向量集合",
    "fields": [
        {
            "name": "id",  # 向量唯一标识
            "type": "string",
            "is_primary": True
        },
        {
            "name": "vector",  # 文本向量
            "type": "float_vector",
            "dimension": 1024  # 根据Qwen3-Embedding模型(text-embedding-v3)
        },
        {
            "name": "chunk_id",  # 关联的知识片段ID
            "type": "int64"
        },
        {
            "name": "doc_id",  # 关联的文档ID
            "type": "int64"
        },
        {
            "name": "content",  # 原始文本内容
            "type": "string"
        },
        {
            "name": "category",  # 知识分类
            "type": "string"
        }
    ],
    "indexes": [
        {
            "field_name": "vector",
            "index_type": "IVF_FLAT",  # 或 HNSW
            "metric_type": "L2",  # 或 COSINE
            "params": {"nlist": 1024}
        }
    ]
}
```

#### 2.2.2 检索参数
```java
// Java SDK检索参数示例
{
    "topK": 5,  // 返回最相关的5个文档
    "metricType": "COSINE",  // 余弦相似度
    "params": {
        "nprobe": 10  // 搜索的簇数量
    }
}
```

## 3. RESTful API 接口设计

### 3.1 基础规范

- **Base URL**: `/api/v1`
- **认证方式**: JWT Token
- **数据格式**: JSON
- **统一响应格式**:
```json
{
    "code": 200,
    "message": "success",
    "data": {...},
    "timestamp": 1704067200000,
    "requestId": "uuid"
}
```

### 3.2 问答接口

#### 3.2.1 发送消息
```http
POST /api/v1/chat/message
Content-Type: application/json

{
    "conversationId": 123,  // 可选,为空则创建新会话
    "message": "什么是Spring Boot框架?",
    "context": [...]  // 可选,前几轮对话
}
```

**响应**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "messageId": 456,
        "answer": "Spring Boot是一个开源的Java框架,用于快速创建独立的、生产级别的Spring应用程序。它简化了Spring应用的配置和部署过程...",
        "sources": [
            {
                "docId": 1,
                "title": "Spring Boot入门教程",
                "content": "...",
                "similarity": 0.92
            }
        ],
        "usage": {
            "promptTokens": 120,
            "completionTokens": 156,
            "totalTokens": 276
        }
    }
}
```

#### 3.2.2 获取对话历史
```http
GET /api/v1/chat/conversations?page=1&size=10
```

**响应**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "total": 25,
        "page": 1,
        "size": 10,
        "list": [
            {
                "id": 123,
                "title": "Spring框架相关问题",
                "lastMessage": "Spring Boot的主要特性是什么?",
                "messageCount": 8,
                "createdAt": "2024-01-01 10:00:00",
                "updatedAt": "2024-01-01 11:30:00"
            }
        ]
    }
}
```

#### 3.2.3 获取对话详情
```http
GET /api/v1/chat/conversations/{conversationId}/messages
```

### 3.3 知识库管理接口

#### 3.3.1 上传文档
```http
POST /api/v1/knowledge/docs
Content-Type: multipart/form-data

{
    "file": <文件>,
    "title": "Java基础教程",
    "description": "Java语言基础知识点总结",
    "category": "编程语言"
}
```

**响应**:
```json
{
    "code": 200,
    "message": "文档上传成功,正在处理中...",
    "data": {
        "docId": 789,
        "title": "Java基础教程",
        "status": "PROCESSING"  // PROCESSING, COMPLETED, FAILED
    }
}
```

#### 3.3.2 获取文档列表
```http
GET /api/v1/knowledge/docs?category=编程语言&status=ACTIVE&page=1&size=10
```

#### 3.3.3 更新文档
```http
PUT /api/v1/knowledge/docs/{docId}
Content-Type: application/json

{
    "title": "更新的标题",
    "description": "更新的描述",
    "category": "新的分类"
}
```

#### 3.3.4 删除文档
```http
DELETE /api/v1/knowledge/docs/{docId}
```

#### 3.3.5 重新索引文档
```http
POST /api/v1/knowledge/docs/{docId}/reindex
```

### 3.4 用户管理接口

#### 3.4.1 用户注册
```http
POST /api/v1/auth/register
Content-Type: application/json

{
    "username": "zhangsan",
    "password": "123456",
    "email": "zhangsan@example.com",
    "nickname": "张三"
}
```

#### 3.4.2 用户登录
```http
POST /api/v1/auth/login
Content-Type: application/json

{
    "username": "zhangsan",
    "password": "123456"
}
```

**响应**:
```json
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "userId": 1,
        "username": "zhangsan",
        "nickname": "张三",
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "expireAt": 1704153600000
    }
}
```

### 3.5 系统配置接口

#### 3.5.1 获取系统配置
```http
GET /api/v1/admin/config
```

#### 3.5.2 更新系统配置
```http
PUT /api/v1/admin/config
Content-Type: application/json

{
    "rag.top_k": 5,
    "rag.temperature": 0.7,
    "rag.max_tokens": 1000
}
```

## 5. 后端项目结构

### 5.1 Maven项目结构

```
EchoCampus-Bot/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/echocampus/bot/
│   │   │   │       ├── EchoCampusBotApplication.java          # 启动类
│   │   │   │       ├── config/                                # 配置类
│   │   │   │       │   ├── SecurityConfig.java                # Spring Security配置
│   │   │   │       │   ├── WebConfig.java                    # Web配置(CORS)
│   │   │   │       │   ├── AiConfig.java                     # AI服务配置
│   │   │   │       │   ├── AiServiceConfig.java              # AI服务配置(OpenAI兼容)
│   │   │   │       │   ├── MilvusConfig.java                 # Milvus向量数据库配置
│   │   │   │       │   └── MyBatisPlusConfig.java            # MyBatis-Plus配置
│   │   │   │       ├── controller/                            # 控制器
│   │   │   │       │   ├── ChatController.java               # 聊天接口
│   │   │   │       │   ├── KnowledgeController.java          # 知识库接口
│   │   │   │       │   └── UserController.java               # 用户接口
│   │   │   │       ├── service/                               # 服务层
│   │   │   │       │   ├── ChatService.java                  # 聊天服务
│   │   │   │       │   ├── KnowledgeService.java             # 知识库服务
│   │   │   │       │   ├── AiService.java                    # AI服务
│   │   │   │       │   └── UserService.java                  # 用户服务
│   │   │   │       ├── service/impl/                          # 服务实现
│   │   │   │       │   ├── ChatServiceImpl.java
│   │   │   │       │   ├── KnowledgeServiceImpl.java
│   │   │   │       │   ├── AiServiceImpl.java
│   │   │   │       │   └── UserServiceImpl.java
│   │   │   │       ├── mapper/                                # MyBatis Mapper
│   │   │   │       │   ├── UserMapper.java
│   │   │   │       │   ├── ConversationMapper.java
│   │   │   │       │   ├── MessageMapper.java
│   │   │   │       │   ├── KnowledgeDocMapper.java
│   │   │   │       │   └── KnowledgeChunkMapper.java
│   │   │   │       ├── entity/                                # 实体类
│   │   │   │       │   ├── User.java
│   │   │   │       │   ├── Conversation.java
│   │   │   │       │   ├── Message.java
│   │   │   │       │   ├── KnowledgeDoc.java
│   │   │   │       │   └── KnowledgeChunk.java
│   │   │   │       ├── dto/                                   # 数据传输对象
│   │   │   │       │   ├── ChatRequest.java
│   │   │   │       │   ├── ChatResponse.java
│   │   │   │       │   └── KnowledgeDocDTO.java
│   │   │   │       ├── parser/                                # 文档解析器
│   │   │   │       │   ├── DocumentParser.java               # 解析器接口
│   │   │   │       │   ├── PdfDocumentParser.java            # PDF解析器
│   │   │   │       │   ├── TxtDocumentParser.java            # TXT解析器
│   │   │   │       │   ├── MarkdownDocumentParser.java       # Markdown解析器
│   │   │   │       │   ├── WordDocumentParser.java           # Word解析器
│   │   │   │       │   ├── PptDocumentParser.java            # PPT解析器
│   │   │   │       │   └── DocumentParserFactory.java        # 解析器工厂
│   │   │   │       ├── utils/                                 # 工具类
│   │   │   │       │   ├── JwtUtil.java                      # JWT工具
│   │   │   │       │   ├── FileUtil.java                     # 文件工具
│   │   │   │       │   └── JsonUtil.java                     # JSON工具
│   │   │   │       └── constants/                             # 常量类
│   │   │   │           └── AppConstants.java
│   │   │   └── resources/
│   │   │       ├── application.yml                            # 主配置文件
│   │   │       ├── application-local.yml                      # 本地环境配置
│   │   │       ├── application-dev.yml                        # 开发环境配置
│   │   │       ├── application-prod.yml                       # 生产环境配置
│   │   │       └── mapper/                                    # MyBatis XML映射
│   │   └── test/                                              # 测试代码
│   ├── pom.xml                                                # Maven配置
│   └── uploads/                                               # 上传文件目录
├── frontend/
│   ├── public/                                                # 公共资源
│   │   ├── index.html
│   │   └── favicon.ico
│   ├── src/
│   │   ├── assets/                                            # 静态资源
│   │   │   ├── images/
│   │   │   └── styles/
│   │   ├── components/                                        # 公共组件
│   │   │   ├── ChatMessage.vue
│   │   │   ├── FileUpload.vue
│   │   │   └── Pagination.vue
│   │   ├── views/                                             # 页面组件
│   │   │   ├── Chat.vue                                       # 聊天主页面
│   │   │   ├── Knowledge.vue                                  # 知识库管理
│   │   │   ├── Settings.vue                                   # 系统设置
│   │   │   └── Login.vue                                      # 登录页面
│   │   ├── api/                                               # API接口
│   │   │   ├── chat.ts
│   │   │   ├── knowledge.ts
│   │   │   └── user.ts
│   │   ├── stores/                                            # Pinia状态管理
│   │   │   ├── chat.ts
│   │   │   ├── knowledge.ts
│   │   │   └── user.ts
│   │   ├── utils/                                             # 工具函数
│   │   │   ├── request.ts                                     # Axios封装
│   │   │   ├── auth.ts
│   │   │   └── format.ts
│   │   ├── types/                                             # TypeScript类型定义
│   │   │   ├── chat.d.ts
│   │   │   ├── knowledge.d.ts
│   │   │   └── user.d.ts
│   │   ├── router/                                            # 路由配置
│   │   │   └── index.ts
│   │   ├── App.vue
│   │   └── main.ts
│   ├── .env                                                   # 环境变量
│   ├── .env.development
│   ├── .env.production
│   ├── vite.config.ts                                         # Vite配置
│   ├── tsconfig.json                                          # TypeScript配置
│   └── package.json
├── docker-compose.yml                                         # Docker编排
├── Dockerfile                                                 # Docker镜像构建
└── README.md                                                  # 项目说明
```

### 5.2 核心模块说明

#### 5.2.1 配置模块 (config)
- **SecurityConfig**: Spring Security配置,JWT认证过滤器
- **WebConfig**: Web配置,CORS跨域配置
- **AiConfig**: AI服务配置,阿里云百炼平台Qwen3-Embedding和DeepSeek API密钥
- **AiServiceConfig**: AI服务配置(OpenAI兼容接口)
- **MilvusConfig**: Milvus客户端配置,连接向量数据库
- **MyBatisPlusConfig**: MyBatis-Plus配置,分页插件

#### 5.2.2 控制器模块 (controller)
- **ChatController**: 聊天相关接口
- **KnowledgeController**: 知识库管理接口
- **UserController**: 用户认证相关接口

#### 5.2.3 服务模块 (service)
- **ChatService**: 对话服务,处理问答逻辑
- **KnowledgeService**: 知识库服务,文档CRUD和向量化
- **AiService**: AI服务,调用Embedding和LLM API
- **UserService**: 用户服务,用户认证和管理

#### 5.2.4 文档解析模块 (parser)
- **DocumentParser**: 文档解析器接口
- **PdfDocumentParser**: PDF文档解析器
- **TxtDocumentParser**: TXT文档解析器
- **MarkdownDocumentParser**: Markdown文档解析器
- **WordDocumentParser**: Word文档解析器
- **PptDocumentParser**: PPT文档解析器
- **DocumentParserFactory**: 文档解析器工厂,根据文件类型选择解析器

#### 5.2.5 工具模块 (utils)
- **JwtUtil**: JWT令牌生成和验证
- **FileUtil**: 文件处理工具(上传、解析、分块)
- **JsonUtil**: JSON序列化工具

### 5.3 核心依赖 (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot Starters (3.2.1) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.5</version>
    </dependency>

    <!-- 数据库与连接池 -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid-spring-boot-3-starter</artifactId>
        <version>1.2.20</version>
    </dependency>

    <!-- 向量数据库 -->
    <dependency>
        <groupId>io.milvus</groupId>
        <artifactId>milvus-sdk-java</artifactId>
        <version>2.3.4</version>
    </dependency>

    <!-- RAG相关 -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.28.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.28.0</version>
    </dependency>

    <!-- 文档解析 -->
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>3.0.1</version>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi</artifactId>
        <version>5.2.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.2.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-scratchpad</artifactId>
        <version>5.2.5</version>
    </dependency>
    <dependency>
        <groupId>com.vladsch.flexmark</groupId>
        <artifactId>flexmark-all</artifactId>
        <version>0.64.8</version>
    </dependency>
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.17.2</version>
    </dependency>

    <!-- HTTP客户端 -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>

    <!-- API文档与JWT -->
    <dependency>
        <groupId>com.github.xiaoymin</groupId>
        <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        <version>4.4.0</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>

    <!-- 常用工具 -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.15.1</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <version>5.8.24</version>
    </dependency>
</dependencies>
```

## 6. 前端项目结构

### 6.1 Vue.js项目结构

```
frontend/
├── public/                              # 公共资源
│   ├── index.html
│   └── favicon.ico
├── src/
│   ├── assets/                          # 静态资源
│   │   ├── images/
│   │   └── styles/
│   ├── components/                      # 公共组件
│   │   ├── ChatMessage.vue
│   │   ├── FileUpload.vue
│   │   └── Pagination.vue
│   ├── views/                           # 页面组件
│   │   ├── Chat.vue                     # 聊天主页面
│   │   ├── Knowledge.vue                # 知识库管理
│   │   ├── Settings.vue                 # 系统设置
│   │   └── Login.vue                    # 登录页面
│   ├── api/                             # API接口
│   │   ├── chat.ts
│   │   ├── knowledge.ts
│   │   └── user.ts
│   ├── stores/                          # Pinia状态管理
│   │   ├── chat.ts
│   │   ├── knowledge.ts
│   │   └── user.ts
│   ├── utils/                           # 工具函数
│   │   ├── request.ts                   # Axios封装
│   │   ├── auth.ts
│   │   └── format.ts
│   ├── types/                           # TypeScript类型定义
│   │   ├── chat.d.ts
│   │   ├── knowledge.d.ts
│   │   └── user.d.ts
│   ├── router/                          # 路由配置
│   │   └── index.ts
│   ├── App.vue
│   └── main.ts
├── .env                                 # 环境变量
├── .env.development
├── .env.production
├── vite.config.ts                       # Vite配置
├── tsconfig.json                        # TypeScript配置
└── package.json
```

### 6.2 核心依赖 (package.json)

```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.5",
    "pinia": "^2.1.7",
    "axios": "^1.6.5",
    "ant-design-vue": "^4.1.1",
    "@ant-design/icons-vue": "^7.0.1",
    "marked": "^11.1.1",
    "highlight.js": "^11.9.0",
    "marked-highlight": "^2.1.0",
    "dayjs": "^1.11.10",
    "@vueuse/core": "^10.7.2"
  },
  "devDependencies": {
    "@types/node": "^20.11.5",
    "typescript": "^5.3.3",
    "vite": "^5.0.11",
    "vue-tsc": "^1.8.27",
    "@vitejs/plugin-vue": "^5.0.3",
    "unplugin-vue-components": "^0.26.0",
    "unplugin-auto-import": "^0.17.3"
  }
}
```

## 7. 部署方案

### 7.1 云服务器部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                        云服务器                              │
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │   Nginx         │    │   Docker        │               │
│  │   (80/443)      │────│   容器化部署     │               │
│  └─────────────────┘    └─────────────────┘               │
│                                │                            │
│              ┌─────────────────┼─────────────────┐          │
│              │                 │                 │          │
│      ┌───────▼───────┐ ┌───────▼───────┐ ┌───────▼───────┐  │
│      │ 后端服务       │ │ 前端静态文件   │ │ PostgreSQL    │  │
│      │ (Spring Boot) │ │ (Vue.js Build)│ │ 数据库        │  │
│      │ 端口: 8080    │ │               │ │ 端口: 5432    │  │
│      └───────────────┘ └───────────────┘ └───────────────┘  │
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐               │
│  │   Milvus        │    │   外部API      │               │
│  │   向量数据库    │    │   阿里云百炼    │               │
│  │   端口: 19530   │    │   DeepSeek     │               │
│  └─────────────────┘    └─────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Docker部署配置

#### 7.2.1 docker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQL数据库
  postgres:
    image: postgres:15
    container_name: echocampus-postgres
    environment:
      POSTGRES_DB: echocampus_bot
      POSTGRES_USER: echocampus
      POSTGRES_PASSWORD: your_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    restart: unless-stopped

  # Milvus向量数据库
  milvus:
    image: milvusdb/milvus:v2.3.4
    container_name: echocampus-milvus
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus_data:/var/lib/milvus
    ports:
      - "19530:19530"
    depends_on:
      - etcd
      - minio
    restart: unless-stopped

  # Milvus依赖 - etcd
  etcd:
    image: quay.io/coreos/etcd:v3.5.0
    container_name: echocampus-etcd
    environment:
      ETCD_ADVERTISE_CLIENT_URLS: "http://etcd:2379"
      ETCD_LISTEN_CLIENT_URLS: "http://0.0.0.0:2379"
      ETCD_DATA_DIR: "/etcd"
    volumes:
      - etcd_data:/etcd
    restart: unless-stopped

  # Milvus依赖 - MinIO
  minio:
    image: minio/minio:RELEASE.2023-01-25T00-19-54Z
    container_name: echocampus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/data
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    restart: unless-stopped

  # 后端服务
  backend:
    build: ./backend
    container_name: echocampus-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: echocampus_bot
      DB_USER: echocampus
      DB_PASSWORD: your_password
      MILVUS_HOST: milvus
      MILVUS_PORT: 19530
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - milvus
    volumes:
      - ./backend/uploads:/app/uploads
    restart: unless-stopped

  # 前端Nginx
  nginx:
    image: nginx:alpine
    container_name: echocampus-nginx
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./frontend/dist:/usr/share/nginx/html
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    restart: unless-stopped

volumes:
  postgres_data:
  milvus_data:
  etcd_data:
  minio_data:
```

#### 7.2.2 Dockerfile (后端)

```dockerfile
# 多阶段构建
FROM maven:3.8-openjdk-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# 运行阶段
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 7.2.3 Nginx配置

```nginx
events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    upstream backend {
        server backend:8080;
    }

    server {
        listen 80;
        server_name your-domain.com;

        # 前端静态资源
        location / {
            root /usr/share/nginx/html;
            index index.html;
            try_files $uri $uri/ /index.html;
        }

        # API代理
        location /api {
            proxy_pass http://backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # WebSocket支持(可选)
        location /ws {
            proxy_pass http://backend;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
        }
    }
}
```

### 7.3 部署步骤

#### 7.3.1 准备工作
1. 购买云服务器(推荐配置: 4核8G, 100G SSD)
2. 安装 Docker 和 Docker Compose
3. 配置安全组规则(开放 80, 443, 8080, 5432, 19530 端口)

#### 7.3.2 部署流程
```bash
# 1. 克隆项目代码
git clone https://github.com/yourusername/EchoCampus-Bot.git
cd EchoCampus-Bot

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件,配置数据库密码、AI API密钥等

# (可选) 配置部署信息
cp .deployment.env.example .deployment.env
# 编辑 .deployment.env 文件,配置阿里云镜像仓库和服务器部署信息

# 3. 构建并启动服务
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 查看日志
docker-compose logs -f backend

# 6. 初始化数据库
docker-compose exec backend java -jar app.jar --init-db

# 7. 初始化Milvus集合
docker-compose exec backend java -jar app.jar --init-milvus
```

#### 7.3.3 访问系统
- 前端地址: `http://your-server-ip/`
- 后端API: `http://your-server-ip/api/v1/`
- Swagger文档: `http://your-server-ip/api/doc.html`

## 8. 开发计划

### 8.1 第一阶段 (Week 1): 基础框架搭建
- [x] 创建Spring Boot项目,配置基本依赖
- [x] 设计数据库表结构,创建实体类
- [x] 实现MyBatis-Plus集成和基础CRUD
- [x] 创建Vue.js前端项目,配置路由和基础布局
- [x] 实现用户登录注册功能

### 8.2 第二阶段 (Week 2): 核心功能开发
- [x] 集成Milvus向量数据库,创建集合
- [x] 实现阿里云百炼平台Qwen3-Embedding API调用(text-embedding-v3)
- [x] 实现DeepSeek API调用(deepseek-chat)
- [x] 开发RAG问答核心逻辑
- [x] 实现对话历史管理
- [x] 开发聊天界面,支持消息展示和发送

### 8.3 第三阶段 (Week 3): 完善和部署
- [x] 开发知识库管理功能(上传、删除、更新)
- [x] 实现文档解析和向量化
- [ ] 优化前端界面,提升用户体验
- [ ] 编写单元测试和集成测试
- [x] 容器化部署到云服务器
- [ ] 编写、完善设计报告

### 8.4 第四阶段 (Week 4): 新功能开发 + 长期运维

## 9. 关键技术点

### 9.1 RAG实现要点
1. **文档预处理**: 
   - 支持多种格式(PDF、TXT、MD、DOCX、PPT、PPTX)
   - 使用LangChain4j智能文本切块(递归分割、语义保持)
   - 配置灵活的分隔符策略(段落、句子、标点符号)
   - 支持chunk重叠保持上下文连贯性
   - 去除噪声(特殊字符、格式标记)

2. **向量检索**:
   - 选择合适的向量维度(Qwen3-Embedding: 1024维)
   - 设置合理的Top-K值(通常3-5个)
   - 相似度阈值过滤(避免不相关内容)

3. **Prompt工程**:
```
你是一名专业的IT知识问答助手。请基于以下提供的参考资料,准确、简洁地回答用户的问题。

参考资料:
{context}

用户问题: {question}

回答要求:
1. 只使用参考资料中的信息
2. 如果问题与参考资料无关,请说明"抱歉,我暂时无法回答这个问题"
3. 语言简洁清晰,避免冗长
4. 可以适当使用列表、代码块等格式

回答:
```

### 9.2 性能优化
- **数据库优化**: 为常用查询字段添加索引
- **缓存策略**: 对热点知识库内容进行Redis缓存
- **异步处理**: 文档上传和向量化使用异步队列
- **连接池**: 合理配置数据库和HTTP连接池大小

#### 9.2.1 文档解析与Chunking最佳实践

**1. 使用LangChain4j进行智能Chunking**

```java
// 递归字符分割器(推荐用于中文文档)
DocumentSplitter splitter = DocumentSplitters.recursive(
    500,  // chunkSize - 每个chunk的最大字符数
    50,   // overlapSize - chunk之间的重叠字符数
    1,    // minimumChunkSizeToEmbed - 最小chunk大小
    // 分隔符优先级(从高到低)
    "\n\n",  // 双换行(段落)
    "\n",    // 单换行
    "。",    // 中文句号
    "！",    // 中文感叹号
    "？",    // 中文问号
    ".",     // 英文句号
    "!",     // 英文感叹号
    "?",     // 英文问号
    " ",     // 空格
    ""       // 无分隔符(强制分割)
);
```

**2. 不同文档类型的Chunking策略**

| 文档类型 | 推荐策略 | Chunk大小 | 重叠大小 | 说明 |
|---------|---------|----------|---------|------|
| PDF | 递归分割 | 800-1000 | 100-150 | 按段落和句子切分 |
| Markdown | 递归分割 | 600-900 | 80-120 | 保留标题结构 |
| TXT | 按段落分割 | 500-800 | 50-100 | 纯文本按段落 |
| DOCX | 递归分割 | 700-900 | 80-120 | 保留文档结构 |
| PPT/PPTX | 递归分割 | 400-600 | 50-80 | 按幻灯片切分 |
| 代码文件 | 按行分割 | 300-500 | 30-50 | 保持代码完整性 |

**3. 文档解析器实现**

```java
// PDF解析器
public class PdfDocumentParser implements DocumentParser {
    public String parse(String filePath) {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return cleanText(stripper.getText(document));
        }
    }
}

// PPT/PPTX解析器
public class PptDocumentParser implements DocumentParser {
    public String parse(String filePath) {
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(filePath))) {
            StringBuilder text = new StringBuilder();
            for (XSLFSlide slide : ppt.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        XSLFTextShape textShape = (XSLFTextShape) shape;
                        text.append(textShape.getText()).append("\n");
                    }
                }
                text.append("\n--- Slide ---\n");
            }
            return text.toString();
        }
    }
}
```

**4. 完整的文档处理流程**

```java
@Async
public void processDocument(Long docId) {
    // 1. 获取文档
    KnowledgeDoc doc = docMapper.selectById(docId);
    
    // 2. 根据文件类型选择解析器
    DocumentParser parser = parserFactory.getParser(doc.getFileType());
    String content = parser.parse(doc.getFilePath());
    
    // 3. 使用LangChain4j进行智能分割
    Document document = Document.from(content);
    DocumentSplitter splitter = getSplitterForFileType(doc.getFileType());
    List<TextSegment> segments = splitter.split(document);
    
    // 4. 批量向量化
    List<float[]> vectors = aiService.getTextEmbeddings(
        segments.stream().map(TextSegment::text).collect(Collectors.toList())
    );
    
    // 5. 存储到Milvus和PostgreSQL
    saveChunks(docId, segments, vectors);
    
    // 6. 更新文档状态
    doc.setStatus("COMPLETED");
    doc.setVectorCount(segments.size());
    docMapper.updateById(doc);
}
```

**5. 配置化Chunking参数**

```yaml
# application.yml
chunking:
  strategy: recursive  # recursive/paragraph/line/character
  max-size: 500
  overlap-size: 50
  min-chunk-size: 1
  separators: '\n\n,\n,。,！,？,.,!,?, ,'
  
  document-types:
    pdf:
      max-size: 800
      overlap-size: 100
    md:
      max-size: 600
      overlap-size: 80
    ppt:
      max-size: 400
      overlap-size: 50
```

### 9.3 安全性考虑
- **API密钥**: 使用环境变量存储,不提交到代码仓库
- **文件上传**: 限制文件类型和大小,防止恶意文件
- **SQL注入**: 使用MyBatis参数化查询
- **XSS防护**: 前端对用户输入进行转义
- **JWT认证**: 使用Spring Security + JWT进行用户认证
- **CORS配置**: 合理配置跨域访问策略

## 10. 测试方案

### 10.1 测试类型
- **单元测试**: 测试Service层和工具类
- **集成测试**: 测试API接口和数据库交互
- **端到端测试**: 测试完整问答流程

### 10.2 测试用例示例
```java
// RAG问答测试
@Test
public void testRagQa() {
    String question = "什么是Spring Boot?";
    ChatResponse response = chatService.sendMessage(question);
    
    assertNotNull(response);
    assertNotNull(response.getAnswer());
    assertTrue(response.getAnswer().contains("Spring"));
    assertTrue(response.getSources().size() > 0);
}

// 知识库上传测试
@Test
public void testUploadDocument() {
    MultipartFile file = new MockMultipartFile(
        "test.pdf", 
        "Java基础教程.pdf", 
        "application/pdf",
        "PDF content".getBytes()
    );
    
    KnowledgeDoc doc = knowledgeService.uploadDocument(file, "Java基础");
    assertNotNull(doc);
    assertEquals("Java基础教程.pdf", doc.getFileName());
    assertEquals("PROCESSING", doc.getStatus());
}
```

## 11. 监控与日志

### 11.1 日志配置
```yaml
# application.yml
logging:
  level:
    com.echocampus.bot: INFO
    io.milvus: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: logs/echocampus-bot.log
    max-size: 100MB
    max-history: 30
```

### 11.2 监控指标
- **API响应时间**: 记录每个接口的耗时
- **AI调用次数**: 统计Embedding和LLM调用频率
- **向量检索性能**: 监控Milvus查询耗时
- **系统资源**: CPU、内存、磁盘使用率

## 12. 总结

本项目采用现代化的技术栈,结合RAG技术实现了一个智能IT知识问答机器人。通过合理的数据库设计、清晰的API接口、友好的前端界面,以及完善的部署方案,确保系统的可用性和可扩展性。

项目亮点:
- ✅ 采用RAG架构,提升回答质量
- ✅ 前后端分离,便于独立开发和部署
- ✅ 向量数据库支持高效的语义检索
- ✅ 容器化部署,易于扩展和维护
- ✅ 完整的知识库管理功能
- ✅ 使用**LangChain4j**进行智能文本切块,保证语义完整性
- ✅ 支持**多种文档格式**(PDF、TXT、MD、DOCX、PPT、PPTX)
- ✅ 灵活的chunking策略配置,根据文档类型自动优化
- ✅ 完整的文档解析器工厂模式实现
- ✅ Spring Security + JWT认证机制
- ✅ 支持邮件通知功能

## 📚 相关文档

- [快速部署指南](./docs/快速部署指南.md) - 本地开发和生产环境部署指南
- [配置审计报告](./docs/配置审计报告.md) - 配置体系优化和安全审计
- [配置迁移指南](./docs/配置迁移指南.md) - 从旧配置迁移到新配置的指南
- [数据库设计](./docs/数据库设计.sql) - PostgreSQL数据库结构设计
- [API接口设计](./docs/API接口设计.yaml) - RESTful API接口文档
- [IT知识问答机器人_项目结构设计](./docs/IT知识问答机器人_项目结构设计.md) - 详细的项目结构设计
- [前端界面设计](./docs/前端界面设计.md) - 前端界面设计规范
- [项目快速入门指南](./docs/项目快速入门指南.md) - 快速开始指南
- [文档解析器实现指南](./docs/文档解析器实现指南.md) - 文档解析器详细实现指南
- [代码质量审查报告](./代码质量审查报告.md) - 代码质量审查报告

## 🔧 配置文件说明

项目包含以下配置文件：

| 文件名 | 说明 | 是否提交到Git |
|--------|------|--------------|
| `.env.example` | 应用环境变量模板 | ✅ 是 |
| `.env` | 实际应用环境变量（包含API密钥等敏感信息） | ❌ 否 |
| `.deployment.env.example` | 部署配置模板 | ✅ 是 |
| `.deployment.env` | 实际部署配置（包含服务器信息、镜像仓库凭证等） | ❌ 否 |
| `docker-compose.dev.yml` | 本地开发环境配置 | ✅ 是 |
| `docker-compose.prod.yml` | 生产环境配置 | ✅ 是 |

**首次使用请执行**：
```bash
# 配置应用环境变量
cp .env.example .env
# 编辑 .env 填入API密钥等信息

# 配置部署信息（仅在需要部署到生产环境时）
cp .deployment.env.example .deployment.env
# 编辑 .deployment.env 填入服务器和镜像仓库信息
```

## 🔗 参考资源

- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [Vue.js 3官方文档](https://vuejs.org/)
- [LangChain4j官方文档](https://docs.langchain4j.dev/)
- [Milvus向量数据库](https://milvus.io/)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [Apache POI](https://poi.apache.org/)
- [DeepSeek API](https://platform.deepseek.com/)
- [阿里云百炼平台](https://bailian.console.aliyun.com/)

---
