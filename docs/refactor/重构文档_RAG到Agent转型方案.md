# EchoCampus-Bot 重构文档：从RAG到Agent的技术转型方案

> **文档版本**: v1.0.0  
> **创建日期**: 2026年1月16日  
> **修订日期**: 2026年2月21日  
> **文档性质**: 项目重构技术规划文书  
> **适用范围**: EchoCampus-Bot 后端服务重构（Java → Python/LangGraph）  
> **保密等级**: 内部技术文档

---

## 文档脉络与结构总览

本文档共分为**十一章**，覆盖从现状分析到实施落地的完整重构生命周期：

| 章节编号 | 章节标题 | 核心内容 | 字数预估 |
|---------|---------|---------|---------|
| 第一章 | 执行摘要与重构背景 | 重构动因、目标定义、范围界定、预期收益 | ~2000字 |
| 第二章 | 现有系统全景分析 | Java后端架构拆解、业务模块梳理、数据流分析、技术债务评估 | ~4000字 |
| 第三章 | 重构技术栈选型论证 | Python生态选型、LangGraph框架论证、pgvector选型依据、各组件对比分析 | ~3000字 |
| 第四章 | 目标架构设计 | Agent整体架构、分层设计、模块划分、智能体核心能力设计 | ~5000字 |
| 第五章 | 数据库迁移方案 | PostgreSQL+Milvus → PostgreSQL+pgvector迁移策略、数据模型转换、索引优化 | ~3000字 |
| 第六章 | 核心模块详细设计 | 智能体管理模块、知识库模块、工具调用模块、对话管理模块等 | ~5000字 |
| 第七章 | 接口设计与系统对接 | RESTful API重新设计、与Golang校园小程序后端对接、认证授权机制 | ~3000字 |
| 第八章 | 技术迁移要点分析 | Java→Python语言迁移、并发模型转换、LangGraph集成方案 | ~3000字 |
| 第九章 | 实施计划与里程碑 | 分阶段实施步骤、时间节点、资源需求、任务分配 | ~2000字 |
| 第十章 | 质量保障与测试策略 | 测试体系设计、代码审查规范、性能指标、安全测试 | ~2500字 |
| 第十一章 | 风险评估与应对方案 | 风险识别矩阵、应对措施、回滚方案、灰度发布策略 | ~2000字 |

---

**文档脉络说明**：

- **第一~二章**为"现状诊断"阶段，聚焦于全面理解当前系统；
- **第三~四章**为"架构决策"阶段，提供技术选型依据和目标架构蓝图；
- **第五~七章**为"方案设计"阶段，覆盖数据层、业务层、接口层的具体设计；
- **第八章**为"技术桥梁"，解决从Java到Python的迁移路径问题；
- **第九~十一章**为"实施保障"阶段，确保重构可控、可量化、可回退。

以下为各章节详细内容。

---

## 第一章 执行摘要与重构背景

### 1.1 重构动因

EchoCampus-Bot 作为基于 RAG（Retrieval-Augmented Generation）技术的智能校园问答系统，当前版本（v1.0.0）已实现核心的知识问答、文档管理和用户认证功能。然而，随着业务需求演进和技术生态发展，系统面临以下关键挑战，构成本次重构的直接驱动力：

**（1）业务迭代需求——从"被动问答"到"主动Agent"**

当前系统仅支持被动式的问答交互：用户提问→检索知识库→生成回答。这一模式在面对复杂校园场景时存在明显局限。例如，学生询问"帮我查看本周课表并提醒明天的考试"，系统无法拆解任务、调用多个工具、协调多步骤执行。向 Agent（智能体）架构的转型，将赋予系统**任务规划**、**多工具调用**、**记忆管理**和**自主决策**等高阶能力，实现从"知识检索工具"到"校园智能助手"的质变。

**（2）技术生态对接——与Golang校园小程序后端集成**

团队已有基于 Golang 开发的校园小程序后端服务（涵盖课程管理、校园通知、生活服务等），当前 Java 后端与 Golang 服务之间缺乏有效的对接机制。Python 生态在 AI/LLM 领域拥有最成熟的框架支持（LangChain、LangGraph、LlamaIndex 等），且 Python 的 FastAPI 框架天然支持异步和高性能 API 开发，更便于与 Golang 服务进行微服务间通信。

**（3）基础设施简化——消除 Milvus 运维负担**

Milvus 向量数据库虽性能优越，但其依赖 etcd + MinIO 的部署架构带来了显著的运维复杂度和资源消耗。在当前 3.6GB 内存的服务器上，Milvus 及其依赖组件占用了约 60% 的内存资源。PostgreSQL 的 pgvector 扩展已在业界广泛验证，能够在单一数据库中同时承载关系型数据和向量检索需求，大幅降低运维成本和部署复杂度。

**（4）AI 框架生态——Python 的压倒性优势**

Java 的 LangChain4j（v0.28.0）虽能满足基本 RAG 需求，但与 Python 生态的 LangChain/LangGraph 相比，在社区活跃度、功能丰富度、更新频率、第三方集成数量等方面差距显著。LangGraph 提供了生产级的 Agent 状态管理、工作流编排和人机协同能力，是构建复杂 Agent 系统的最佳选择。

### 1.2 重构目标定义

本次重构设定以下核心目标，作为后续设计决策的评判标准：

| 目标编号 | 目标描述 | 验收标准 |
|---------|---------|---------|
| G-01 | 完成从 RAG 架构到 Agent 架构的转型 | 系统具备任务规划、工具调用、记忆管理、自主决策能力 |
| G-02 | 实现 Java 到 Python 的语言迁移 | 后端服务全部使用 Python 实现，基于 FastAPI 框架 |
| G-03 | 集成 LangGraph 框架实现智能体工作流 | 核心问答流程基于 LangGraph StateGraph 编排 |
| G-04 | 完成数据库从 PG+Milvus 到 PG+pgvector 的迁移 | 向量检索功能正常，性能偏差不超过 20% |
| G-05 | 设计与 Golang 校园后端的对接接口 | 定义清晰的数据交换协议和认证机制 |
| G-06 | 保持对现有前端的向后兼容 | 前端无需修改或仅需最小化调整 |
| G-07 | 系统稳定性和性能不低于当前水平 | 通过压力测试验证响应时间和吞吐量 |

### 1.3 重构范围界定

**在范围内（In Scope）**：
- 后端服务的完整重写（Java Spring Boot → Python FastAPI）
- AI 编排框架迁移（LangChain4j → LangGraph）
- 向量数据库迁移（Milvus → pgvector）
- API 接口重新设计（保持向后兼容）
- Agent 核心能力设计与实现
- 与 Golang 校园后端的对接接口设计
- Docker 部署方案重新设计
- 数据迁移方案与工具

**不在范围内（Out of Scope）**：
- 前端 Vue.js 应用的重构（仅做必要的 API 适配）
- Golang 校园小程序后端的修改
- AI 模型的训练或微调
- 移动端 App 的开发

### 1.4 预期收益

| 收益维度 | 具体收益 | 量化指标 |
|---------|---------|---------|
| 运维效率 | 消除 Milvus+etcd+MinIO 运维负担 | 部署组件减少 60%（6个→2个） |
| 资源消耗 | 降低服务器内存占用 | 预计内存降低 40-50% |
| 开发效率 | Python 生态 AI 开发效率更高 | 新功能开发周期缩短 30-50% |
| 功能能力 | Agent 架构支持复杂交互 | 支持多工具调用、任务规划 |
| 可扩展性 | 与校园生态对接 | 支持调用 Golang 后端 10+ API |
| 社区支持 | LangGraph 社区活跃度高 | 周更新频率，1000+ 集成方案 |

### 1.5 重构策略选择

经过对增量重构与整体重构两种策略的评估，本项目采用**整体重构（Big-Bang Rewrite）**策略，理由如下：

1. **语言切换不可增量**：从 Java 到 Python 的迁移无法在同一代码库中增量完成。
2. **架构范式转变**：从 RAG 管道到 Agent 状态图的转变涉及根本性的架构重设计。
3. **数据库架构变更**：pgvector 替换 Milvus 需要重新设计向量存储方案。
4. **项目规模可控**：当前后端代码量约 127 个 Java 文件，体量适中，整体重构风险可控。

**风险缓释措施**：通过保持 API 接口兼容性、制定详细的数据迁移方案、设计灰度发布策略来降低整体重构的风险。

---

## 第二章 现有系统全景分析

### 2.1 系统架构概述

当前 EchoCampus-Bot v1.0.0 采用经典的前后端分离架构，后端基于 Spring Boot 3.2.1 构建，核心技术组件如下：

| 层级 | 技术选型 | 版本 |
|------|---------|------|
| Web框架 | Spring Boot + Spring MVC | 3.2.1 |
| ORM层 | MyBatis-Plus | 3.5.5 |
| 关系型数据库 | PostgreSQL | 15 |
| 向量数据库 | Milvus（+etcd+MinIO） | 2.3.4 |
| AI编排 | LangChain4j | 0.28.0 |
| 认证授权 | Spring Security + JWT（jjwt 0.12.3） | - |
| HTTP客户端 | OkHttp | 4.12.0 |
| 文档解析 | Apache PDFBox + Apache POI + Flexmark | 3.0.1 / 5.2.5 / 0.64.8 |
| API文档 | Knife4j (OpenAPI3) | 4.4.0 |
| 连接池 | Druid | 1.2.20 |

### 2.2 后端分层架构拆解

后端采用标准 Spring Boot 分层架构（共 112 个 Java 主源文件 + 15 个测试文件），包结构如下：

```
com.echocampus.bot/
├── annotation/          # 自定义注解（@OpLog, @RequireRole）
├── common/              # 通用封装（Result, ResultCode, BusinessException, PageResult）
├── config/              # 配置类（12个）: Security, Milvus, AI, ThreadPool, RateLimit, Swagger等
├── controller/          # 控制器（6个）: Chat, Knowledge, User, System, OperationLog, Health
├── dto/
│   ├── request/         # 请求DTO（7个）: ChatRequest, LoginRequest, RegisterWithCodeRequest等
│   └── response/        # 响应DTO（5个）: ChatResponse, StreamChatResponse, LoginResponse等
├── entity/              # 数据库实体（10个）: User, Conversation, Message, KnowledgeDoc等
├── filter/              # 过滤器: JwtAuthenticationFilter, XssFilter, XssHttpServletRequestWrapper
├── interceptor/         # 拦截器: RoleInterceptor
├── mapper/              # MyBatis Mapper接口（9个）
├── parser/              # 文档解析器: 工厂+策略模式（接口+工厂+7个实现）
├── service/             # 服务接口（15个）+ impl实现（15个）+ tool（1个）
├── task/                # 定时任务: DataCleanupTask, VerificationCodeCleanupTask
└── utils/               # 工具类: JwtUtil, DateTimeUtil, PasswordUtil
```

### 2.3 核心业务模块清单

基于源码分析，现有系统包含以下核心业务模块：

#### 2.3.1 智能问答模块（AI核心）

该模块是系统的技术核心，包含两种 RAG 工作模式，由 `rag.enhanced-mode` 配置开关控制：

**传统 RAG 模式**（`RagService` → `RagServiceImpl`）：
- 流程：用户问题 → EmbeddingService 向量化 → MilvusService 向量检索 → 构建 Context → LlmService 生成回答
- 特点：每次问答必定执行知识库检索，适用于明确的知识型问题

**增强 RAG 模式**（`EnhancedRagService` → `EnhancedRagServiceImpl`）：
- 流程：构建上下文查询（含最近 3 轮对话历史）→ EnhancedLlmService（带 Tool Calling 的 DeepSeek API 调用）→ AI 自主判断是否调用 `KnowledgeSearchTool` → 生成回答
- 特点：AI 自主决定是否检索知识库，支持最多 5 次工具调用迭代
- 技术实现：手动构建 OpenAI 兼容格式的 `tools` JSON，解析 `tool_calls` 响应

**关键服务**：

| 服务 | 职责 | 核心方法 |
|------|------|---------|
| `DeepSeekChatServiceImpl` | LLM 调用（OkHttp 直接调用 DeepSeek API） | `chat()`, `chatStream()` |
| `EnhancedDeepSeekLlmServiceImpl` | 带工具调用的 LLM（迭代式 Tool Calling） | `chatWithTools()`, `chatWithToolsStream()` |
| `AliyunEmbeddingServiceImpl` | 阿里云百炼 Embedding（text-embedding-v3, 1024维） | `embed()`, `embedBatch()` |
| `MilvusServiceImpl` | Milvus 向量库操作（CRUD、检索、集合管理） | `search()`, `insertVectors()`, `deleteByDocId()` |
| `KnowledgeSearchTool` | LangChain4j Tool，封装向量检索逻辑供 LLM 调用 | `searchKnowledge(query)` |
| `TextChunkServiceImpl` | LangChain4j 文本分割（分层递归策略） | `splitText()` |

#### 2.3.2 知识库管理模块

| 功能 | 实现细节 |
|------|---------|
| 文档上传 | 支持 7 种格式（PDF/TXT/MD/DOCX/DOC/PPT/PPTX），工厂模式选择解析器 |
| 文档解析 | DocumentParserFactory + 各格式 Parser（PDFBox/POI/Flexmark） |
| 文本切块 | LangChain4j `DocumentSplitters.recursive()`，按文件类型调参 |
| 向量化存储 | 分批 Embedding（每批 10 条），写入 Milvus + PG 元数据 |
| 处理进度 | `DocumentProgressService` 通过 SSE 实时推送进度 |
| 异步处理 | `@Async` + `@TransactionalEventListener` 事务提交后异步执行 |

**文本切块策略**（分文件类型）：

| 文件类型 | 最大字符数 | 重叠字符数 | 分割优先级 |
|---------|----------|----------|----------|
| PDF | 800 | 100 | 段落 → 句子 → 字符 |
| Markdown | 600 | 80 | 段落 → 句子 → 字符 |
| DOCX/DOC | 700 | 90 | 段落 → 句子 → 字符 |
| PPT/PPTX | 400 | 50 | 幻灯片 → 句子 → 字符 |
| TXT | 500 | 50 | 段落 → 句子 → 字符 |

#### 2.3.3 用户与认证模块

| 功能 | 实现细节 |
|------|---------|
| 用户注册 | 支持邮箱验证码注册（SMTP 发送） |
| 用户登录 | JWT 令牌（HMAC-SHA 签名，24 小时有效期） |
| 密码管理 | BCrypt 加密存储，支持邮箱验证码重置 |
| 权限控制 | 双重机制：Spring Security 路径级别 + `@RequireRole` 方法级别 |
| XSS 防护 | `XssFilter` + `XssHttpServletRequestWrapper` 全局过滤 |
| 限流机制 | `RateLimitConfig` 并发限流（100全局/50 SSE）+ 时间窗口限流 |

#### 2.3.4 系统管理模块

| 功能 | 实现细节 |
|------|---------|
| 操作日志 | AOP 切面（`OperationLogAspect`），自动记录带 `@OpLog` 注解的操作 |
| 系统配置 | `system_config` 表存储，支持动态修改 RAG 参数 |
| 数据清理 | 定时任务清理软删除数据和过期验证码 |
| 健康检查 | `/api/v1/health` 检查 PG 和 Milvus 连接状态 |

### 2.4 数据模型总览

当前系统共有 **11 张 PostgreSQL 表 + 1 个 Milvus 向量集合**：

**PostgreSQL 表**：

| 表名 | 记录数量级 | 核心字段 | 关系 |
|------|----------|---------|------|
| `users` | 百级 | id, username, password, email, role, status | 主表 |
| `conversations` | 千级 | id, user_id, title, message_count, status | users 1:N |
| `messages` | 万级 | id, conversation_id, sender_type, content, metadata(JSONB) | conversations 1:N |
| `knowledge_docs` | 百级 | id, title, file_type, category, status, process_status, vector_count | users 1:N |
| `knowledge_chunks` | 千级 | id, doc_id, content, vector_id, chunk_index, metadata(JSONB) | knowledge_docs 1:N |
| `knowledge_categories` | 十级 | id, name, parent_id, sort_order, doc_count | 自关联 |
| `email_verification_codes` | 百级 | email, code, type, expired_at, used | - |
| `search_logs` | 千级 | query, retrieved_chunks(JSONB), response_time_ms | users 1:N |
| `operation_logs` | 千级 | operation_type, resource_type, ip_address, execution_time | users 1:N |
| `system_config` | 十级 | config_key, config_value, config_type | - |
| `system_statistics` | 百级 | stat_date, stat_type, user_count, message_count | - |

**Milvus 集合**（`echocampus_knowledge`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VarChar (PK) | 向量唯一标识 |
| vector | FloatVector(1024) | 文本嵌入向量 |
| chunk_id | Int64 | 关联 knowledge_chunks.id |
| doc_id | Int64 | 关联 knowledge_docs.id |
| content | VarChar | 原始文本内容 |
| category | VarChar | 知识分类 |

**索引**: IVF_FLAT, COSINE 度量, nlist=1024, nprobe=10

### 2.5 API 接口清单

当前系统共暴露 **28 个 RESTful API 端点**（前缀 `/api/v1`）：

| 类别 | 端点数 | 主要接口 |
|------|-------|---------|
| 用户认证 | 8 | 登录、注册、发送验证码、获取/更新个人信息、修改密码 |
| 聊天会话 | 7 | 发送消息（同步/流式）、会话 CRUD、获取会话消息 |
| 知识库管理 | 8 | 文档 CRUD、重新索引、分类列表、处理进度（SSE） |
| 操作日志 | 4 | 查询日志、日志详情、用户最近日志、我的日志 |
| 系统管理 | 1 | 手动数据清理 |
| 健康检查 | 2 | 健康状态、服务信息 |

**SSE（Server-Sent Events）事件类型**：

| 事件类型 | 用途 | 数据格式 |
|---------|------|---------|
| `status` | 处理状态更新 | `{"stage": "SEARCHING"}` |
| `source` | 知识来源信息 | `{"docTitle": "...", "similarity": 0.89}` |
| `content` | 内容流式片段 | `{"text": "..."}` |
| `done` | 生成完成信号 | `{"messageId": 123}` |
| `error` | 错误信息 | `{"message": "..."}` |

### 2.6 当前系统技术债务与瓶颈

基于代码质量审查报告 v3 及源码分析，当前系统存在以下技术债务：

| 编号 | 问题 | 严重程度 | 重构中的处理方式 |
|------|------|---------|----------------|
| D-01 | 会话权限校验缺失（越权访问风险） | 🔴 高 | 重构时从架构层面解决 |
| D-02 | Controller 层未传递 userId 进行权限校验 | 🔴 高 | FastAPI 依赖注入统一处理 |
| D-03 | 重复向量搜索导致双倍 Embedding API 调用 | 🟡 中 | 重构时消除 |
| D-04 | OkHttpClient 重复创建，连接池未共享 | 🟡 中 | Python httpx 统一管理 |
| D-05 | 异常被吞噬（Milvus 删除失败时数据不一致） | 🟡 中 | 事务一致性重新设计 |
| D-06 | XSS 过滤过于激进（误过滤合法内容） | 🟢 低 | 采用更精确的过滤策略 |
| D-07 | 缺少 Redis 缓存层 | 🟡 中 | 重构时引入 |
| D-08 | 前端组件抽象不足（Chat.vue 1477 行） | 🟢 低 | 不在本次范围内 |

### 2.7 外部系统集成点

| 外部服务 | 用途 | 通信方式 | 协议/格式 |
|---------|------|---------|----------|
| 阿里云百炼平台 | Embedding 服务（text-embedding-v3） | HTTPS | OpenAI 兼容 REST API |
| DeepSeek API | LLM 推理 + Tool Calling | HTTPS | OpenAI 兼容 REST API |
| SMTP 邮件服务 | 发送验证码邮件 | SMTP/TLS | Spring Mail |
| Golang 校园后端（待对接） | 课程/通知/校园服务数据 | HTTP/gRPC | RESTful/Protobuf |

---

## 第三章 重构技术栈选型论证

### 3.1 技术栈总览

重构后的系统将采用以下技术栈：

| 层级 | 当前技术 | 目标技术 | 选型理由 |
|------|---------|---------|---------|
| 编程语言 | Java 17 | **Python 3.11+** | AI 生态最成熟，LLM 框架首选语言 |
| Web框架 | Spring Boot 3.2.1 | **FastAPI 0.110+** | 原生异步、自动 OpenAPI 文档、高性能 |
| AI编排 | LangChain4j 0.28.0 | **LangGraph 0.2+** | 生产级 Agent 状态管理，支持复杂工作流 |
| ORM | MyBatis-Plus 3.5.5 | **SQLAlchemy 2.0 + asyncpg** | Python 最成熟的 ORM，原生异步支持 |
| 向量检索 | Milvus 2.3.4 | **pgvector 0.7+** | 统一 PG 存储，消除运维复杂度 |
| 认证授权 | Spring Security + JWT | **python-jose + FastAPI Depends** | 轻量、灵活的认证方案 |
| HTTP客户端 | OkHttp 4.12 | **httpx（异步）** | 原生 async/await 支持 |
| 数据校验 | Spring Validation | **Pydantic v2** | 强类型验证，性能优越 |
| 任务队列 | Spring @Async + ThreadPool | **Celery + Redis** 或 **asyncio** | 分布式任务处理 |
| 缓存 | 无 | **Redis 7+** | 热点数据缓存、会话缓存、限流 |
| 文档解析 | PDFBox + POI + Flexmark | **unstructured / PyMuPDF + python-docx** | 更丰富的格式支持 |
| API文档 | Knife4j (Swagger) | **FastAPI 内置 OpenAPI** | 自动生成，零配置 |
| 容器化 | Docker Compose（6容器） | **Docker Compose（2-3容器）** | 大幅简化 |

### 3.2 Python 语言选型论证

**选择 Python 的核心理由**：

1. **AI 生态主导地位**：LangChain、LangGraph、LlamaIndex、OpenAI SDK、HuggingFace 等核心 AI 框架均以 Python 为第一语言。Python 社区拥有最活跃的 AI 工具链，新功能发布通常领先其他语言数月。

2. **开发效率优势**：Python 动态类型和简洁语法使得 AI 应用的原型开发和迭代效率远高于 Java。配合 Pydantic 的类型注解，可兼顾开发速度和类型安全。

3. **异步编程成熟**：Python 3.11+ 的 asyncio 生态已相当成熟，FastAPI + uvicorn + asyncpg 的组合在 I/O 密集型场景下性能表现优异，完全满足 AI Agent 服务的需求。

4. **社区与人才储备**：Python 在 AI/ML 领域的开发者基数远大于 Java，有利于团队扩展和知识分享。

**性能考量**：虽然 Python 的计算性能低于 Java，但 Agent 服务的瓶颈在于外部 API 调用（Embedding/LLM）的网络延迟（通常 1-5 秒），而非 CPU 计算。Python 的异步 I/O 模型非常适合此类 I/O-bound 场景。

### 3.3 FastAPI 框架选型论证

| 对比维度 | FastAPI | Flask | Django |
|---------|---------|-------|--------|
| 异步支持 | ✅ 原生 async/await | ⚠️ 需 Quart 扩展 | ⚠️ 3.1+ 部分支持 |
| 性能 | ⭐⭐⭐⭐⭐ (uvicorn) | ⭐⭐⭐ | ⭐⭐⭐ |
| 自动 API 文档 | ✅ 内置 OpenAPI/Swagger | ❌ 需扩展 | ❌ 需 DRF |
| 类型验证 | ✅ Pydantic 深度集成 | ❌ 手动 | ⚠️ Serializer |
| 依赖注入 | ✅ 内置 Depends 系统 | ❌ | ❌ |
| SSE 支持 | ✅ StreamingResponse | ⚠️ 需扩展 | ⚠️ 需扩展 |
| 学习曲线 | 低 | 低 | 中 |
| LangGraph 集成 | ✅ 社区最佳实践 | ⚠️ 可行 | ⚠️ 可行 |

**结论**：FastAPI 在异步性能、类型安全、自动文档、依赖注入等方面全面领先，且与 LangGraph 的集成在社区有最多最佳实践，是 AI Agent 服务的最优选择。

### 3.4 LangGraph 框架选型论证

**为什么选择 LangGraph 而非 LangChain Agent？**

| 对比维度 | LangChain Agent | LangGraph |
|---------|----------------|-----------|
| 架构模型 | 链式调用（Chain of Thought） | **状态图（StateGraph）** |
| 流程控制 | 线性/条件链 | **任意图结构（循环、分支、合并）** |
| 状态管理 | 简单传递 | **持久化状态 + 检查点** |
| 人机协同 | 不支持 | **内置 Human-in-the-Loop** |
| 流式输出 | 基础支持 | **细粒度流式（按节点、按事件）** |
| 错误恢复 | 简单重试 | **从检查点恢复** |
| 多 Agent | 不支持 | **内置 Multi-Agent 协作** |
| 工具调用 | 支持 | **ToolNode 自动管理** |
| 生产就绪 | 需额外工作 | **LangGraph Platform 部署** |

**LangGraph 的核心优势**：

1. **StateGraph 状态图**：将 Agent 工作流建模为有向图，每个节点代表一个处理步骤（LLM 调用、工具执行、条件判断），边代表状态转移。这种模型天然适合复杂的 Agent 交互场景。

2. **检查点持久化**：支持将 Agent 状态保存到 PostgreSQL（通过 `langgraph-checkpoint-postgres`），实现对话恢复、中断续接等高级功能。

3. **细粒度流式输出**：支持按节点、按事件类型的流式输出，可精确控制前端展示的内容类型（思考过程、工具调用、最终回答）。

4. **Multi-Agent 架构**：内置多智能体协作能力，未来可扩展为"课程助手"、"校园生活助手"、"就业指导助手"等多个专业智能体协同工作。

### 3.5 PostgreSQL + pgvector 选型论证

**从 Milvus 迁移到 pgvector 的技术依据**：

| 对比维度 | Milvus 2.3.4 | pgvector 0.7+ |
|---------|-------------|---------------|
| 部署复杂度 | 高（需 etcd + MinIO） | **低（PG 扩展，一条命令安装）** |
| 运维成本 | 高（3 个独立服务） | **极低（PG 统一运维）** |
| 内存消耗 | ~1.5GB（含依赖） | **~100MB 增量** |
| 向量维度支持 | 2048+ | **2000（0.7+ 支持更高）** |
| 索引类型 | IVF_FLAT, HNSW 等 | **IVFFlat, HNSW** |
| 检索性能（万级数据） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 检索性能（百万级数据） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 事务支持 | 无 | **完整 ACID 事务** |
| 关系+向量联合查询 | 需跨库 JOIN | **单库原生 JOIN** |
| 数据一致性 | 最终一致性 | **强一致性** |
| 生态成熟度 | 成熟 | **快速成熟中** |
| LangChain/LangGraph集成 | 支持 | **深度支持** |

**关键决策依据**：

1. **数据规模匹配**：当前知识库规模为千级文档、万级向量片段。pgvector 在此规模下性能完全满足需求（<100ms 检索延迟），无需 Milvus 的大规模分布式能力。

2. **数据一致性**：当前系统存在向量删除失败导致数据不一致的问题（技术债务 D-05）。pgvector 与业务数据在同一事务中操作，从根本上消除了一致性问题。

3. **资源节约**：服务器仅 3.6GB 内存，Milvus 三件套占用约 1.5-2GB。迁移后可释放大量资源给 Agent 服务。

4. **运维简化**：生产环境从 6 个 Docker 容器（PG + etcd + MinIO + Milvus + Attu + Backend）减少为 2-3 个容器（PG+pgvector + Redis + Backend）。

### 3.6 其他关键组件选型

| 组件 | 选型 | 替代方案 | 选型理由 |
|------|------|---------|---------|
| 异步任务 | Celery + Redis | asyncio.TaskGroup | Celery 支持分布式部署、任务重试、优先级队列 |
| 缓存 | Redis 7 | 无 | 统一缓存/限流/消息队列/Celery Broker |
| 文档解析 | unstructured | PyMuPDF + python-docx | unstructured 提供统一 API 支持 20+ 格式 |
| 文本分割 | LangChain text_splitters | 自定义实现 | 与 LangGraph 同生态，分割策略丰富 |
| 数据库迁移 | Alembic | 手动 SQL | SQLAlchemy 官方迁移工具，版本化管理 |
| 配置管理 | Pydantic Settings | python-dotenv | 强类型配置，自动环境变量加载 |
| 日志 | structlog + loguru | logging | 结构化日志，支持 JSON 输出 |
| 测试 | pytest + pytest-asyncio | unittest | Python 测试标准，丰富的插件生态 |

---

## 第四章 目标架构设计

### 4.1 架构总览

重构后的 EchoCampus-Bot Agent 系统采用**分层 + 模块化 + 事件驱动**的架构设计，核心理念是将 AI Agent 能力（任务规划、工具调用、记忆管理）与传统业务逻辑（用户认证、CRUD 操作）解耦，形成清晰的职责边界。

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                       EchoCampus-Bot Agent 系统架构                           │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    接入层 (Gateway Layer)                               │ │
│  │                                                                        │ │
│  │   ┌─────────────┐  ┌─────────────┐  ┌──────────────┐  ┌───────────┐ │ │
│  │   │ Web前端(Vue) │  │校园小程序    │  │ Golang后端    │  │ OpenAPI   │ │ │
│  │   │ HTTP/SSE     │  │(微信/H5)    │  │ gRPC/HTTP    │  │ 文档      │ │ │
│  │   └─────────────┘  └─────────────┘  └──────────────┘  └───────────┘ │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│                          FastAPI (ASGI / uvicorn)                           │
│                                    │                                        │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    API路由层 (Router Layer)                             │ │
│  │                                                                        │ │
│  │   /api/v1/chat    /api/v1/knowledge   /api/v1/user   /api/v1/agent   │ │
│  │   /api/v1/system  /api/v1/health      /api/v1/tools                   │ │
│  │                                                                        │ │
│  │   中间件链: CORS → Auth(JWT) → RateLimit → Logging → ErrorHandler    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                   业务服务层 (Service Layer)                            │ │
│  │                                                                        │ │
│  │   ┌──────────────────────────────────────────────────────────────────┐│ │
│  │   │                 Agent 核心 (LangGraph)                           ││ │
│  │   │                                                                  ││ │
│  │   │   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ││ │
│  │   │   │ 任务规划  │  │ 工具调用  │  │ 记忆管理  │  │ 上下文理解    │  ││ │
│  │   │   │ Planner  │  │ ToolNode │  │ Memory   │  │ Context      │  ││ │
│  │   │   └──────────┘  └──────────┘  └──────────┘  └──────────────┘  ││ │
│  │   │                                                                  ││ │
│  │   │   StateGraph → 节点(LLM/工具/条件) → 边(状态转移) → 检查点    ││ │
│  │   └──────────────────────────────────────────────────────────────────┘│ │
│  │                                                                        │ │
│  │   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌─────────────────┐   │ │
│  │   │ 用户服务    │ │ 知识库服务  │ │ 对话服务    │ │ 系统管理服务     │   │ │
│  │   │ UserService│ │ Knowledge  │ │ ChatService│ │ SystemService   │   │ │
│  │   └────────────┘ └────────────┘ └────────────┘ └─────────────────┘   │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                   工具层 (Tools Layer)                                  │ │
│  │                                                                        │ │
│  │   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │ │
│  │   │ 知识库检索    │ │ 校园API调用   │ │ 文档解析      │ │ 邮件发送    │ │ │
│  │   │ RAGTool     │ │ CampusTool  │ │ DocParser   │ │ EmailTool  │ │ │
│  │   └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘ │ │
│  │   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                 │ │
│  │   │ 课表查询      │ │ 成绩查询      │ │ 通知查询      │  ... 可扩展    │ │
│  │   │ ScheduleTool│ │ GradeTool   │ │ NoticeTool  │                 │ │
│  │   └──────────────┘ └──────────────┘ └──────────────┘                 │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                   数据访问层 (Repository Layer)                         │ │
│  │                                                                        │ │
│  │   SQLAlchemy 2.0 (async) + Repository Pattern                         │ │
│  │   UserRepo │ ConversationRepo │ KnowledgeRepo │ VectorRepo            │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                        │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                   基础设施层 (Infrastructure Layer)                     │ │
│  │                                                                        │ │
│  │   ┌─────────────────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │   │ PostgreSQL 15 + pgvector│  │  Redis 7     │  │ 外部AI服务    │    │ │
│  │   │ • 业务数据              │  │  • 缓存      │  │ • Embedding  │    │ │
│  │   │ • 向量数据              │  │  • 限流      │  │ • LLM        │    │ │
│  │   │ • Agent检查点           │  │  • 会话      │  │ • DeepSeek   │    │ │
│  │   └─────────────────────────┘  └──────────────┘  └──────────────┘    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                   横切关注点 (Cross-Cutting Concerns)                   │ │
│  │                                                                        │ │
│  │   日志(structlog) │ 监控(Prometheus) │ 限流(Redis) │ 安全(JWT/CORS)   │ │
│  │   错误处理(全局) │ 配置管理(Pydantic Settings) │ 依赖注入(FastAPI)    │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 分层架构详细说明

#### 4.2.1 接入层（Gateway Layer）

接入层负责处理来自不同客户端的请求。重构后系统将支持三类接入方式：

| 接入方式 | 协议 | 用途 |
|---------|------|------|
| Web 前端（Vue.js） | HTTP REST + SSE | 现有前端应用，保持向后兼容 |
| 校园小程序（微信/H5） | HTTP REST | 未来移动端接入 |
| Golang 校园后端 | HTTP REST / gRPC（预留） | 内部服务间调用 |

#### 4.2.2 API 路由层（Router Layer）

基于 FastAPI 的路由层，采用蓝图（APIRouter）模式组织 API 端点，统一前缀 `/api/v1`。

**中间件链执行顺序**：
```
请求进入 → CORS中间件 → 请求ID注入 → 限流检查(Redis) → 
JWT认证中间件 → 请求日志记录 → 路由分发 → 
响应日志记录 → 异常处理 → 响应返回
```

#### 4.2.3 业务服务层（Service Layer）

业务服务层分为两个子层：

**Agent 核心子层**：基于 LangGraph StateGraph 实现，包含：
- **任务规划器（Planner）**：分析用户意图，决定执行路径
- **工具节点（ToolNode）**：管理和执行已注册的工具
- **记忆管理器（Memory）**：管理对话历史、长期记忆
- **上下文构建器（Context）**：组装 LLM 调用所需的完整上下文

**传统业务子层**：处理与 Agent 无关的标准业务逻辑（用户管理、知识库 CRUD、系统配置等）。

#### 4.2.4 工具层（Tools Layer）

所有可被 Agent 调用的工具统一定义在工具层，每个工具遵循标准接口：

| 工具名称 | 功能描述 | 数据来源 |
|---------|---------|---------|
| `knowledge_search` | 在知识库中检索相关信息 | PostgreSQL + pgvector |
| `campus_schedule` | 查询课程表 | Golang 校园后端 API |
| `campus_grade` | 查询成绩信息 | Golang 校园后端 API |
| `campus_notice` | 查询校园通知 | Golang 校园后端 API |
| `campus_library` | 查询图书馆信息 | Golang 校园后端 API |
| `email_send` | 发送邮件通知 | SMTP 服务 |
| `web_search` | 互联网搜索（预留） | 搜索引擎 API |

工具注册采用声明式方式，支持动态增删工具。

#### 4.2.5 数据访问层（Repository Layer）

采用 Repository 模式封装数据库操作，基于 SQLAlchemy 2.0 异步引擎：

```
Repository 模式:
  BaseRepository (CRUD泛型基类)
    ├── UserRepository
    ├── ConversationRepository
    ├── MessageRepository
    ├── KnowledgeDocRepository
    ├── KnowledgeChunkRepository
    ├── VectorRepository (pgvector 操作)
    └── SystemConfigRepository
```

### 4.3 Agent 核心架构设计

#### 4.3.1 LangGraph StateGraph 设计

Agent 的核心工作流基于 LangGraph 的 StateGraph 建模：

```
                    ┌─────────────┐
                    │   START     │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  路由判断    │ ← 分析用户意图
                    │  (Router)   │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ 直接回答  │  │ RAG问答   │  │ 多工具    │
      │ (闲聊)   │  │ (知识检索)│  │ (复杂任务)│
      └──────┬───┘  └──────┬───┘  └──────┬───┘
             │             │             │
             │             ▼             ▼
             │      ┌──────────┐  ┌──────────┐
             │      │ 向量检索  │  │ 工具调用  │ ←→ 循环调用
             │      │ (pgvector)│  │ (ToolNode)│     最多N次
             │      └──────┬───┘  └──────┬───┘
             │             │             │
             │             ▼             ▼
             │      ┌──────────┐  ┌──────────┐
             │      │ 上下文增强 │  │ 结果整合  │
             │      │ (Context) │  │ (Merge)  │
             │      └──────┬───┘  └──────┬───┘
             │             │             │
             └─────────────┼─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  LLM生成    │ ← 流式输出
                    │  (Generate) │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ 记忆更新     │ ← 保存对话历史
                    │ (Memory)    │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │    END      │
                    └─────────────┘
```

#### 4.3.2 Agent 状态定义

```python
# app/agent/state.py
from __future__ import annotations

import operator
from typing import Annotated, Optional

from langchain_core.messages import AnyMessage
from typing_extensions import TypedDict


class AgentState(TypedDict):
    """Agent 执行状态，贯穿整个 StateGraph 生命周期"""
    messages: Annotated[list[AnyMessage], operator.add]  # 对话消息（自动追加）
    user_id: int                           # 当前用户 ID
    conversation_id: int                   # 当前会话 ID
    intent: str                            # 识别到的用户意图
    retrieved_context: str                 # 检索到的知识上下文
    sources: list[dict]                    # 知识来源列表
    metadata: dict                         # 元数据（耗时、token 数等）
    error: Optional[str]                   # 错误信息
```

> **设计说明**：`messages` 字段使用 `Annotated[list, operator.add]`，这是 LangGraph 的约定——多个节点返回的消息会自动追加而非覆盖，确保完整的对话历史在图中自然流转。

#### 4.3.2.1 StateGraph 骨架代码实现

以下为 Agent 核心工作流的 Python 骨架实现，基于 LangGraph 官方最佳实践：

```python
# app/agent/graph.py
from typing import Literal

from langchain_openai import ChatOpenAI
from langgraph.graph import StateGraph, START, END
from langgraph.prebuilt import ToolNode
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from app.agent.state import AgentState
from app.agent.tools.knowledge_search import knowledge_search
from app.agent.tools.campus_api import campus_schedule, campus_grade, campus_notice
from app.config import settings

# ======================== 工具注册 ========================

tools = [knowledge_search, campus_schedule, campus_grade, campus_notice]

# ======================== LLM 初始化 ========================
# DeepSeek API 兼容 OpenAI 格式，直接使用 ChatOpenAI

llm = ChatOpenAI(
    model="deepseek-chat",
    base_url="https://api.deepseek.com/v1",
    api_key=settings.DEEPSEEK_API_KEY,
    max_tokens=2000,
    streaming=True,
)
llm_with_tools = llm.bind_tools(tools)


# ======================== 节点定义 ========================

async def call_agent(state: AgentState) -> dict:
    """核心 Agent 节点：调用绑定工具的 LLM，由模型自主决定是直接回答还是调用工具"""
    response = await llm_with_tools.ainvoke(state["messages"])
    return {"messages": [response]}


# ToolNode 自动管理工具调用：解析 LLM 返回的 tool_calls，执行对应工具，
# 将结果封装为 ToolMessage 回传
tool_node = ToolNode(tools)


# ======================== 条件路由 ========================

def should_continue(state: AgentState) -> Literal["tool_node", "__end__"]:
    """判断是否需要继续工具调用循环"""
    last_message = state["messages"][-1]
    # 如果 LLM 返回了 tool_calls，则路由到工具节点执行
    if last_message.tool_calls:
        return "tool_node"
    # 否则结束，直接返回最终回答
    return END


# ======================== 构建 StateGraph ========================

async def build_agent_graph() -> StateGraph:
    """构建并编译 Agent 工作流图（带 PostgreSQL 检查点持久化）"""
    async with AsyncPostgresSaver.from_conn_string(settings.DATABASE_URL) as checkpointer:
        builder = StateGraph(AgentState)

        # 添加节点
        builder.add_node("agent", call_agent)
        builder.add_node("tool_node", tool_node)

        # 添加边
        builder.add_edge(START, "agent")                            # 入口 → Agent
        builder.add_conditional_edges("agent", should_continue,     # Agent → 工具 or 结束
                                      ["tool_node", END])
        builder.add_edge("tool_node", "agent")                      # 工具 → 回到 Agent（循环）

        # 编译图（挂载 PostgreSQL 检查点，实现对话持久化）
        return builder.compile(checkpointer=checkpointer)
```

以下为配合 FastAPI 的流式调用示例：

```python
# app/api/v1/chat.py（SSE 流式端点骨架）
from fastapi import APIRouter
from fastapi.responses import StreamingResponse

router = APIRouter()

@router.post("/chat/message/stream")
async def chat_stream(request: ChatRequest):
    """Agent 流式问答端点，通过 SSE 推送执行过程"""
    graph = await build_agent_graph()
    config = {"configurable": {"thread_id": str(request.conversation_id)}}

    async def event_generator():
        async for event in graph.astream_events(
            {"messages": [{"role": "user", "content": request.message}],
             "user_id": request.user_id,
             "conversation_id": request.conversation_id},
            config=config,
            version="v2",
        ):
            kind = event["event"]
            if kind == "on_chat_model_stream":
                # 流式内容片段
                content = event["data"]["chunk"].content
                if content:
                    yield f"event: content\ndata: {{\"text\": \"{content}\"}}\n\n"
            elif kind == "on_tool_start":
                # 工具调用开始
                tool_name = event["name"]
                yield f"event: tool_call\ndata: {{\"tool\": \"{tool_name}\"}}\n\n"
            elif kind == "on_tool_end":
                # 工具调用完成
                yield f"event: tool_result\ndata: {{\"tool\": \"{event['name']}\"}}\n\n"
        yield "event: done\ndata: {}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")
```

> **核心设计要点**：
> 1. `call_agent` → `should_continue` → `tool_node` → `call_agent` 形成**ReAct 循环**，LLM 自主决定何时停止工具调用
> 2. `AsyncPostgresSaver` 将每步状态持久化到 PostgreSQL，支持对话中断后恢复
> 3. `astream_events()` 提供细粒度事件流，可精确区分内容流、工具调用等事件类型，映射到前端 SSE 协议

#### 4.3.3 智能体核心能力实现方案

**（1）任务规划（Task Planning）**

任务规划器基于 LLM 的 Function Calling 能力实现。系统提示词中定义可用工具列表，LLM 自主决定调用哪些工具、以什么顺序调用。对于复杂任务（如"查看本周课表并提醒明天的考试"），LLM 会分解为多个工具调用步骤依次执行。

**规划策略**：
- **ReAct 模式**：Reasoning + Acting 交替进行，每次工具调用后 LLM 重新评估下一步
- **最大迭代次数**：设置上限（默认 8 次）防止无限循环
- **超时保护**：单次 Agent 执行总时间不超过 120 秒

**（2）工具调用（Tool Calling）**

工具调用基于 LangGraph 的 ToolNode 机制实现，所有工具遵循统一接口规范：

- 输入：JSON Schema 定义的参数结构
- 输出：标准化的 ToolMessage 格式
- 错误处理：工具执行异常自动转化为错误消息返回给 LLM
- 权限控制：部分工具需要特定权限（如管理员工具）

**（3）记忆管理（Memory Management）**

系统实现三层记忆架构：

| 记忆层级 | 存储位置 | 生命周期 | 用途 |
|---------|---------|---------|------|
| 工作记忆 | LangGraph State | 单次 Agent 执行 | 当前任务的中间状态 |
| 短期记忆 | Redis + PostgreSQL | 单个会话 | 对话历史（最近 10 轮） |
| 长期记忆 | PostgreSQL | 用户级别持久化 | 用户偏好、历史摘要 |

短期记忆通过 LangGraph 的 `langgraph-checkpoint-postgres` 实现持久化，支持对话中断后恢复。

**（4）上下文理解（Context Understanding）**

上下文理解模块负责将多来源信息整合为 LLM 可理解的上下文：

- **对话历史窗口**：最近 N 轮对话（可配置，默认 10 轮 / 20 条消息）
- **知识检索结果**：pgvector 检索的相关知识片段
- **工具执行结果**：各工具返回的结构化数据
- **用户画像**：用户角色、偏好等上下文信息
- **系统提示词**：包含角色定义、安全规则、输出格式要求

### 4.4 项目目录结构设计

```
echocampus-agent/
├── pyproject.toml                     # 项目元数据与依赖定义（Poetry/uv）
├── alembic.ini                        # 数据库迁移配置
├── Dockerfile                         # 容器构建文件
├── docker-compose.yml                 # 容器编排
├── .env.example                       # 环境变量模板
├── README.md                          # 项目说明
│
├── alembic/                           # 数据库迁移脚本
│   ├── versions/                      # 迁移版本目录
│   └── env.py                         # Alembic 环境配置
│
├── app/                               # 应用主目录
│   ├── __init__.py
│   ├── main.py                        # FastAPI 应用入口
│   ├── config.py                      # 配置管理（Pydantic Settings）
│   │
│   ├── api/                           # API 路由层
│   │   ├── __init__.py
│   │   ├── deps.py                    # 公共依赖（认证、数据库会话等）
│   │   └── v1/                        # API v1 版本
│   │       ├── __init__.py
│   │       ├── router.py              # 总路由注册
│   │       ├── chat.py                # 聊天路由
│   │       ├── knowledge.py           # 知识库路由
│   │       ├── user.py                # 用户路由
│   │       ├── agent.py               # Agent 路由（新增）
│   │       ├── system.py              # 系统管理路由
│   │       └── health.py              # 健康检查路由
│   │
│   ├── core/                          # 核心组件
│   │   ├── __init__.py
│   │   ├── security.py                # JWT认证、密码哈希
│   │   ├── middleware.py              # 中间件（CORS、日志、限流）
│   │   ├── exceptions.py             # 自定义异常
│   │   └── response.py               # 统一响应格式
│   │
│   ├── agent/                         # Agent 核心（LangGraph）
│   │   ├── __init__.py
│   │   ├── graph.py                   # StateGraph 定义（主工作流）
│   │   ├── state.py                   # AgentState 状态定义
│   │   ├── nodes/                     # 图节点
│   │   │   ├── __init__.py
│   │   │   ├── router.py             # 意图路由节点
│   │   │   ├── retriever.py          # RAG 检索节点
│   │   │   ├── generator.py          # LLM 生成节点
│   │   │   └── memory.py             # 记忆管理节点
│   │   ├── tools/                     # Agent 工具
│   │   │   ├── __init__.py
│   │   │   ├── base.py               # 工具基类
│   │   │   ├── knowledge_search.py   # 知识库检索工具
│   │   │   ├── campus_api.py         # 校园API工具集
│   │   │   └── email_tool.py         # 邮件工具
│   │   └── prompts/                   # 提示词模板
│   │       ├── __init__.py
│   │       ├── system.py             # 系统提示词
│   │       └── templates.py          # 各场景提示词模板
│   │
│   ├── services/                      # 业务服务层
│   │   ├── __init__.py
│   │   ├── user_service.py           # 用户服务
│   │   ├── chat_service.py           # 对话服务
│   │   ├── knowledge_service.py      # 知识库服务
│   │   ├── embedding_service.py      # Embedding 服务
│   │   ├── document_service.py       # 文档处理服务
│   │   ├── email_service.py          # 邮件服务
│   │   └── system_service.py         # 系统管理服务
│   │
│   ├── repositories/                  # 数据访问层
│   │   ├── __init__.py
│   │   ├── base.py                   # 基础 Repository
│   │   ├── user_repo.py
│   │   ├── conversation_repo.py
│   │   ├── message_repo.py
│   │   ├── knowledge_repo.py
│   │   └── vector_repo.py           # pgvector 操作
│   │
│   ├── models/                        # 数据模型
│   │   ├── __init__.py
│   │   ├── database.py               # 数据库连接引擎
│   │   ├── base.py                   # SQLAlchemy Base
│   │   ├── user.py                   # 用户模型
│   │   ├── conversation.py           # 会话模型
│   │   ├── message.py                # 消息模型
│   │   ├── knowledge.py              # 知识文档/片段模型
│   │   └── system.py                 # 系统配置/日志模型
│   │
│   ├── schemas/                       # Pydantic 模型（DTO）
│   │   ├── __init__.py
│   │   ├── user.py                   # 用户请求/响应模型
│   │   ├── chat.py                   # 聊天请求/响应模型
│   │   ├── knowledge.py              # 知识库请求/响应模型
│   │   ├── agent.py                  # Agent 请求/响应模型
│   │   └── common.py                 # 通用模型（分页、响应封装）
│   │
│   ├── parsers/                       # 文档解析器
│   │   ├── __init__.py
│   │   ├── base.py                   # 解析器基类
│   │   ├── factory.py                # 解析器工厂
│   │   ├── pdf_parser.py
│   │   ├── docx_parser.py
│   │   ├── markdown_parser.py
│   │   ├── txt_parser.py
│   │   └── ppt_parser.py
│   │
│   └── utils/                         # 工具类
│       ├── __init__.py
│       ├── text_splitter.py          # 文本分割器
│       └── helpers.py                # 通用辅助函数
│
├── tests/                             # 测试目录
│   ├── conftest.py                   # 测试配置和 fixtures
│   ├── unit/                         # 单元测试
│   ├── integration/                  # 集成测试
│   └── e2e/                          # 端到端测试
│
├── scripts/                           # 运维脚本
│   ├── migrate_data.py               # 数据迁移脚本
│   ├── init_db.py                    # 数据库初始化
│   └── seed_data.py                  # 种子数据
│
└── docs/                              # 项目文档
```

### 4.5 模块间通信机制

| 通信类型 | 场景 | 实现方式 |
|---------|------|---------|
| 同步调用 | Service → Repository | Python 异步方法直接调用 |
| 事件驱动 | 文档上传 → 异步处理 | Celery 任务队列 / asyncio.create_task |
| 流式响应 | Agent → 前端 | FastAPI StreamingResponse (SSE) |
| 服务间调用 | Agent → Golang 后端 | httpx 异步 HTTP 调用 |
| 状态持久化 | LangGraph → PostgreSQL | langgraph-checkpoint-postgres |

---

## 第五章 数据库迁移方案

### 5.1 迁移策略概述

数据库迁移涉及两个维度：
1. **关系型数据迁移**：PostgreSQL → PostgreSQL（保留，仅调整表结构）
2. **向量数据迁移**：Milvus → PostgreSQL + pgvector（架构性变更）

**迁移原则**：
- 业务数据零丢失
- 迁移过程可回退
- 向量检索质量不降级
- 支持增量迁移（分批执行）

### 5.2 pgvector 部署与配置

pgvector 作为 PostgreSQL 扩展安装，生产环境推荐配置：

```sql
-- 安装 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证安装
SELECT * FROM pg_extension WHERE extname = 'vector';

-- 配置参数优化（postgresql.conf）
-- shared_buffers = 256MB          # 根据可用内存调整
-- effective_cache_size = 1GB      # 缓存大小
-- maintenance_work_mem = 128MB    # 建索引时的内存
-- max_parallel_workers = 4        # 并行查询 worker 数
```

### 5.3 数据模型转换

#### 5.3.1 保留表（结构微调）

以下表基本保留，仅做少量字段调整以适应 Agent 架构：

| 表名 | 调整内容 |
|------|---------|
| `users` | 新增 `preferences JSONB`（用户偏好，Agent 个性化） |
| `conversations` | 新增 `agent_mode VARCHAR(20)`（使用的 Agent 模式标识） |
| `messages` | `metadata` 字段扩展，存储 Agent 执行轨迹 |
| `knowledge_docs` | 无变化 |
| `knowledge_categories` | 无变化 |
| `email_verification_codes` | 无变化 |
| `search_logs` | 重命名为 `agent_execution_logs`，扩展字段 |
| `operation_logs` | 无变化 |
| `system_config` | 新增 Agent 相关配置项 |
| `system_statistics` | 新增 Agent 调用统计字段 |

#### 5.3.2 核心变更——向量存储重设计

**当前方案**（Milvus 集合 + PG knowledge_chunks 表分离存储）：

```
knowledge_chunks (PostgreSQL)          echocampus_knowledge (Milvus)
┌─────────────────────────┐           ┌──────────────────────────┐
│ id                      │           │ id (PK, VarChar)        │
│ doc_id                  │     ←→    │ chunk_id (Int64)        │
│ content                 │           │ vector (FloatVector 1024)│
│ vector_id ──────────────│──────→    │ doc_id (Int64)          │
│ chunk_index             │           │ content (VarChar)        │
│ metadata                │           │ category (VarChar)       │
└─────────────────────────┘           └──────────────────────────┘
```

**目标方案**（pgvector 统一存储）：

```sql
-- 修改 knowledge_chunks 表，新增向量列
ALTER TABLE knowledge_chunks ADD COLUMN embedding vector(1024);

-- 创建向量索引（HNSW，推荐用于中小规模数据集）
CREATE INDEX idx_knowledge_chunks_embedding 
ON knowledge_chunks 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- 删除旧的 vector_id 字段（迁移完成后）
-- ALTER TABLE knowledge_chunks DROP COLUMN vector_id;
```

**重构后的 `knowledge_chunks` 表完整定义**：

```sql
CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL REFERENCES knowledge_docs(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_type VARCHAR(20) DEFAULT 'TEXT',
    content TEXT NOT NULL,
    content_hash VARCHAR(64),
    embedding vector(1024),              -- pgvector 向量列（替代 Milvus）
    page_number INTEGER,
    metadata JSONB DEFAULT '{}',
    token_count INTEGER DEFAULT 0,
    start_position INTEGER,
    end_position INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    CONSTRAINT chk_embedding_dimension CHECK (vector_dims(embedding) = 1024 OR embedding IS NULL)
);

-- 索引
CREATE INDEX idx_chunks_doc_id ON knowledge_chunks(doc_id);
CREATE INDEX idx_chunks_embedding ON knowledge_chunks 
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 200);
CREATE INDEX idx_chunks_content_hash ON knowledge_chunks(content_hash);
```

#### 5.3.3 新增表

```sql
-- Agent 检查点表（LangGraph 自动管理）
-- 由 langgraph-checkpoint-postgres 自动创建

-- Agent 工具调用日志表
CREATE TABLE agent_tool_calls (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT REFERENCES conversations(id) ON DELETE CASCADE,
    message_id BIGINT REFERENCES messages(id) ON DELETE SET NULL,
    tool_name VARCHAR(100) NOT NULL,
    tool_input JSONB NOT NULL,
    tool_output JSONB,
    execution_time_ms INTEGER,
    status VARCHAR(20) DEFAULT 'SUCCESS',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_calls_conversation ON agent_tool_calls(conversation_id);
CREATE INDEX idx_tool_calls_tool_name ON agent_tool_calls(tool_name);
```

### 5.4 向量检索实现方案

#### 5.4.1 相似度检索 SQL

```sql
-- 基础向量检索
SELECT 
    kc.id,
    kc.content,
    kc.doc_id,
    kd.title AS doc_title,
    kd.category,
    1 - (kc.embedding <=> $1::vector) AS similarity  -- 余弦相似度
FROM knowledge_chunks kc
JOIN knowledge_docs kd ON kc.doc_id = kd.id
WHERE kd.status = 'ACTIVE'
  AND kc.embedding IS NOT NULL
  AND 1 - (kc.embedding <=> $1::vector) > $2         -- 相似度阈值
ORDER BY kc.embedding <=> $1::vector                  -- 按距离排序
LIMIT $3;                                              -- Top-K
```

**关键优势**：向量检索与关系型过滤在同一 SQL 中完成，无需跨库 JOIN。

**Python（SQLAlchemy + pgvector）实现**：

```python
# app/repositories/vector_repo.py
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from pgvector.sqlalchemy import Vector  # pip install pgvector

from app.models.knowledge import KnowledgeChunk, KnowledgeDoc


async def search_similar_chunks(
    session: AsyncSession,
    query_embedding: list[float],
    top_k: int = 5,
    similarity_threshold: float = 0.4,
    category: str | None = None,
) -> list[dict]:
    """
    基于 pgvector 的向量相似度检索

    Args:
        session: SQLAlchemy 异步会话
        query_embedding: 查询向量（1024 维）
        top_k: 返回结果数量
        similarity_threshold: 最低相似度阈值
        category: 知识分类过滤（可选）
    """
    # 使用 pgvector 的 cosine_distance 计算余弦距离，1 - distance = similarity
    similarity = (
        1 - KnowledgeChunk.embedding.cosine_distance(query_embedding)
    ).label("similarity")

    query = (
        select(
            KnowledgeChunk.id,
            KnowledgeChunk.content,
            KnowledgeChunk.doc_id,
            KnowledgeDoc.title.label("doc_title"),
            KnowledgeDoc.category,
            similarity,
        )
        .join(KnowledgeDoc, KnowledgeChunk.doc_id == KnowledgeDoc.id)
        .where(KnowledgeDoc.status == "ACTIVE")
        .where(KnowledgeChunk.embedding.is_not(None))
        .where(similarity > similarity_threshold)
        .order_by(KnowledgeChunk.embedding.cosine_distance(query_embedding))
        .limit(top_k)
    )

    if category:
        query = query.where(KnowledgeDoc.category == category)

    result = await session.execute(query)
    return [dict(row._mapping) for row in result.all()]
```

```python
# app/models/knowledge.py（SQLAlchemy Model 中的 pgvector 列定义）
from sqlalchemy import Column, BigInteger, Text, Integer, String, ForeignKey
from sqlalchemy.dialects.postgresql import JSONB
from pgvector.sqlalchemy import Vector

from app.models.base import Base


class KnowledgeChunk(Base):
    __tablename__ = "knowledge_chunks"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    doc_id = Column(BigInteger, ForeignKey("knowledge_docs.id", ondelete="CASCADE"), nullable=False)
    chunk_index = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    content_hash = Column(String(64))
    embedding = Column(Vector(1024))          # pgvector 向量列，1024 维
    metadata_ = Column("metadata", JSONB, default={})
    token_count = Column(Integer, default=0)
```

> **对比原系统**：原 Java 版需要通过 OkHttp 调用 Milvus SDK 执行向量检索，再跨库 JOIN PostgreSQL 获取元数据，存在数据一致性风险（技术债务 D-05）。重构后向量与元数据在同一事务中操作，从根本上消除了一致性问题。

#### 5.4.2 索引策略优化

| 索引类型 | 适用场景 | 参数 | 特点 |
|---------|---------|------|------|
| HNSW | 中小规模（<100万），高查询频率 | m=16, ef_construction=200 | 查询快，构建慢，内存占用大 |
| IVFFlat | 大规模（>100万），可接受略低精度 | lists=100 | 构建快，查询略慢 |

**推荐**：当前知识库规模（千级文档、万级向量），选择 HNSW 索引。

#### 5.4.3 性能对比预估

| 指标 | Milvus (IVF_FLAT) | pgvector (HNSW) | 差异 |
|------|-------------------|-----------------|------|
| 万级向量 Top-5 检索 | ~5ms | ~10ms | +5ms，可接受 |
| 十万级向量 Top-5 检索 | ~15ms | ~30ms | +15ms，可接受 |
| 百万级向量 Top-5 检索 | ~50ms | ~100ms | +50ms，需评估 |
| 写入吞吐 | ~5000 vec/s | ~2000 vec/s | 文档处理场景影响小 |
| 内存消耗 | ~1.5GB (含etcd+MinIO) | ~100MB 增量 | 节省 ~1.4GB |

**结论**：在当前数据规模下，pgvector 性能完全满足需求，且综合运维成本远低于 Milvus。

### 5.5 数据迁移工具与流程

#### 5.5.1 迁移流程

```
Phase 1: 准备阶段
  ├── 安装 pgvector 扩展
  ├── 执行 DDL 变更（ALTER TABLE 新增 embedding 列）
  ├── 创建 HNSW 索引
  └── 备份当前数据

Phase 2: 数据迁移
  ├── 从 Milvus 导出向量数据（pymilvus 批量查询）
  ├── 按 chunk_id 匹配写入 knowledge_chunks.embedding 列
  ├── 验证数据完整性（比对记录数、抽样校验向量值）
  └── 验证检索质量（对比新旧系统 Top-K 结果的一致性）

Phase 3: 验证阶段
  ├── 执行预定义的 QA 测试集（50+ 问题）
  ├── 对比 Milvus vs pgvector 的检索结果重合率（目标 > 90%）
  ├── 性能基线测试（响应时间、吞吐量）
  └── 确认无数据丢失

Phase 4: 切换阶段
  ├── 应用代码切换到 pgvector
  ├── 关闭 Milvus 相关服务
  ├── 清理 vector_id 字段（可延后）
  └── 更新监控告警配置
```

#### 5.5.2 数据一致性验证

| 验证项目 | 方法 | 通过标准 |
|---------|------|---------|
| 记录数一致 | COUNT(*) 对比 | Milvus 向量数 = PG embedding 非空数 |
| 向量值正确 | 抽样 100 条向量比对 | L2 距离 < 1e-6 |
| 检索质量 | 50 个标准问题 Top-5 对比 | 结果重合率 > 90% |
| 关系完整性 | chunk_id/doc_id 外键校验 | 零悬挂引用 |

### 5.6 回滚方案

若迁移后发现严重问题，可执行以下回滚操作：

1. **数据回滚**：knowledge_chunks 表保留 vector_id 字段，Milvus 数据不删除（保留 30 天）
2. **代码回滚**：Git 版本回退到迁移前的 commit
3. **服务回滚**：Docker Compose 切换回包含 Milvus 的配置文件

---

## 第六章 核心模块详细设计

### 6.1 智能体管理模块

#### 6.1.1 模块职责

智能体管理模块是 Agent 架构的中枢，负责 Agent 实例的生命周期管理、工作流编排、状态持久化和流式输出控制。

**职责边界**：

| 职责 | 描述 |
|------|------|
| Agent 实例创建 | 根据用户请求创建 Agent 实例，配置工具集和提示词 |
| 工作流编排 | 基于 LangGraph StateGraph 管理节点执行顺序 |
| 状态持久化 | 通过 langgraph-checkpoint-postgres 保存检查点 |
| 流式输出 | 将 Agent 执行过程中的各类事件实时推送到客户端 |
| 超时管理 | 控制 Agent 单次执行的最大时长 |
| 错误恢复 | 从检查点恢复中断的 Agent 执行 |

#### 6.1.2 StateGraph 工作流详细设计

Agent 工作流包含以下核心节点和转移边：

**节点定义**：

| 节点名称 | 功能 | 输入 | 输出 |
|---------|------|------|------|
| `route_intent` | 意图路由：分析用户消息，决定处理路径 | messages | intent 标识 |
| `retrieve_knowledge` | 知识检索：向量相似度检索 + 关系过滤 | query, filters | retrieved_context, sources |
| `call_tools` | 工具调用：执行 LLM 决定调用的工具 | tool_calls | tool_results |
| `generate_response` | 回答生成：基于上下文调用 LLM 生成最终回答 | context, messages | generation |
| `update_memory` | 记忆更新：保存对话到数据库、更新会话状态 | messages, generation | - |

**条件边定义**：

| 起始节点 | 条件 | 目标节点 |
|---------|------|---------|
| `route_intent` | intent == "direct_answer" | `generate_response` |
| `route_intent` | intent == "rag_query" | `retrieve_knowledge` |
| `route_intent` | intent == "tool_use" | `generate_response`（带工具） |
| `retrieve_knowledge` | 检索完成 | `generate_response` |
| `generate_response` | 有 tool_calls | `call_tools` |
| `generate_response` | 无 tool_calls | `update_memory` |
| `call_tools` | 工具执行完成 | `generate_response`（循环） |
| `update_memory` | 完成 | END |

#### 6.1.3 流式输出协议设计

Agent 执行过程中的 SSE 事件协议（保持与原系统兼容，并扩展新事件类型）：

| 事件类型 | 触发时机 | 数据格式 | 兼容性 |
|---------|---------|---------|-------|
| `status` | Agent 状态变更 | `{"stage": "ROUTING\|SEARCHING\|GENERATING\|TOOL_CALLING"}` | ✅ 兼容 |
| `source` | 知识检索返回来源 | `{"docId": 1, "title": "...", "similarity": 0.89}` | ✅ 兼容 |
| `content` | LLM 生成内容流式片段 | `{"text": "..."}` | ✅ 兼容 |
| `tool_call` | Agent 调用工具（新增） | `{"tool": "campus_schedule", "input": {...}}` | 🆕 新增 |
| `tool_result` | 工具执行结果（新增） | `{"tool": "campus_schedule", "output": {...}}` | 🆕 新增 |
| `thinking` | Agent 思考过程（新增） | `{"text": "分析用户意图..."}` | 🆕 新增 |
| `done` | 生成完成 | `{"messageId": 123, "tokenUsage": {...}}` | ✅ 兼容 |
| `error` | 错误信息 | `{"code": "...", "message": "..."}` | ✅ 兼容 |

### 6.2 知识库模块

#### 6.2.1 模块职责

知识库模块负责知识的全生命周期管理：上传→解析→切块→向量化→存储→检索→更新→删除。

#### 6.2.2 文档处理流水线

重构后的文档处理流水线基于 Celery 异步任务实现：

```
文件上传 (API层)
    │
    ▼
参数校验 + 文件保存 (Service层, 同步)
    │
    ▼
发布 Celery 任务 (异步)
    │
    ├── 1. 文档解析 (Parser)
    │      └─ 工厂模式: PDFParser / DocxParser / MarkdownParser / TxtParser / PptParser
    │
    ├── 2. 文本切块 (TextSplitter)
    │      └─ LangChain RecursiveCharacterTextSplitter (按文件类型调参)
    │
    ├── 3. 向量化 (EmbeddingService)
    │      └─ 批量调用阿里云百炼 API (每批10条, 批间100ms延迟, 指数退避重试)
    │
    ├── 4. 存储 (Repository)
    │      └─ 事务写入: knowledge_chunks (含 embedding 列) + 更新 knowledge_docs
    │
    └── 5. 进度通知 (SSE推送)
           └─ 通过 Redis Pub/Sub 推送实时进度到客户端
```

**与原系统的关键差异**：

| 差异点 | 原系统 | 重构后 |
|-------|-------|-------|
| 异步机制 | Spring @Async + TransactionalEventListener | Celery 任务队列 |
| 向量存储 | Milvus insertVectors() | PostgreSQL INSERT (含 embedding 列) |
| 进度推送 | SseEmitter + ConcurrentHashMap | Redis Pub/Sub + SSE |
| 事务一致性 | 跨库（PG+Milvus），存在一致性风险 | 单库事务，强一致性 |

#### 6.2.3 文档解析器设计

延续原系统的工厂+策略模式，Python 实现：

| 解析器 | 支持格式 | Python 库 |
|-------|---------|----------|
| `PdfParser` | .pdf | PyMuPDF (fitz) 或 unstructured |
| `DocxParser` | .docx | python-docx 或 unstructured |
| `MarkdownParser` | .md | markdown-it-py 或内置处理 |
| `TxtParser` | .txt | 内置 open() |
| `PptParser` | .pptx | python-pptx 或 unstructured |
| `DocParser` | .doc | antiword + subprocess 或 unstructured |
| `ExcelParser` | .xlsx | openpyxl 或 unstructured |

**推荐方案**：优先采用 `unstructured` 库作为统一解析入口，它提供了对 20+ 文档格式的统一 API 支持。在特定格式解析质量不满足需求时，回退到专用库。

### 6.3 工具调用模块

#### 6.3.1 工具注册与管理

所有 Agent 工具遵循统一的接口规范，通过注册机制供 LangGraph ToolNode 管理：

**工具接口规范**：

| 属性 | 说明 | 类型 |
|------|------|------|
| name | 工具唯一标识 | str |
| description | 工具功能描述（供 LLM 理解） | str |
| args_schema | 参数结构（Pydantic Model / JSON Schema） | Type[BaseModel] |
| return_type | 返回值描述 | str |
| requires_auth | 是否需要用户认证 | bool |
| rate_limit | 调用频率限制 | Optional[int] |

#### 6.3.2 知识库检索工具（核心工具）

```
工具名称: knowledge_search
功能描述: 在校园知识库中搜索与用户问题相关的知识片段
输入参数:
  - query (str): 搜索查询文本
  - top_k (int, 可选, 默认5): 返回结果数量
  - category (str, 可选): 知识分类过滤
  - similarity_threshold (float, 可选, 默认0.4): 最低相似度阈值
输出格式:
  - results: List[{content, doc_title, category, similarity, page_number}]
  - total_found: int
```

#### 6.3.3 校园服务工具集（与 Golang 后端对接）

| 工具名称 | 功能 | 对接的 Golang API | 参数 |
|---------|------|------------------|------|
| `campus_schedule` | 查询课程表 | GET /api/v1/schedule | student_id, week |
| `campus_grade` | 查询成绩 | GET /api/v1/grades | student_id, semester |
| `campus_notice` | 查询校园通知 | GET /api/v1/notices | category, limit |
| `campus_library` | 查询图书馆信息 | GET /api/v1/library | query_type |
| `campus_exam` | 查询考试安排 | GET /api/v1/exams | student_id, upcoming |
| `campus_activity` | 查询校园活动 | GET /api/v1/activities | category, date_range |

**对接方式**：通过 httpx 异步 HTTP 客户端调用 Golang 后端 RESTful API，使用共享的 JWT 或内部 API Key 认证。

### 6.4 对话管理模块

#### 6.4.1 会话生命周期

```
创建会话 → 活跃对话 → 归档/删除
    │          │           │
    │      消息收发       软删除
    │      Agent执行      (保留30天)
    │      记忆更新         │
    │          │          物理删除
    └──────────┘          (定时任务)
```

#### 6.4.2 消息处理流程

重构后的消息处理增强了 Agent 执行轨迹的记录：

1. 接收用户消息
2. 保存用户消息到 messages 表（sender_type='USER'）
3. 加载对话历史（最近 10 轮）
4. 构建 AgentState，启动 LangGraph 执行
5. 流式返回 Agent 执行过程中的事件
6. Agent 执行完成后，保存 Bot 回复到 messages 表（sender_type='BOT'）
7. metadata 字段记录完整的 Agent 执行轨迹（工具调用、检索来源、token 消耗等）

### 6.5 用户认证模块

#### 6.5.1 JWT 认证方案

延续原系统的 JWT 认证方案，使用 `python-jose` 实现：

| 配置项 | 值 |
|-------|------|
| 签名算法 | HS256 |
| Token 有效期 | 24 小时 |
| 刷新策略 | 发放 Refresh Token（7 天有效） |
| 存储 | 无状态（Token 自包含） |
| 黑名单 | Redis 存储已注销的 Token |

**改进点**：新增 Refresh Token 机制，支持 Token 无感刷新，提升用户体验。

#### 6.5.2 权限模型

```
角色:
  USER  - 普通用户: 对话、知识检索、个人信息管理
  ADMIN - 管理员: 用户管理、知识库管理、系统配置、日志查看

权限检查路径:
  FastAPI Depends → get_current_user() → 解析 JWT → 查询用户 → 注入 User 对象
  FastAPI Depends → require_admin() → get_current_user() → 校验 role == ADMIN
```

### 6.6 系统配置与监控模块

#### 6.6.1 配置管理

采用分层配置方案：

| 配置层级 | 存储位置 | 优先级 | 示例 |
|---------|---------|-------|------|
| 环境变量 | .env 文件 / Docker 环境变量 | 最高 | 数据库密码、API Key |
| 应用配置 | Pydantic Settings | 中 | 服务端口、日志级别 |
| 动态配置 | system_config 数据库表 | 按需 | RAG 参数、Agent 行为参数 |

#### 6.6.2 监控指标

| 指标类别 | 具体指标 | 采集方式 |
|---------|---------|---------|
| Agent 性能 | 平均执行时间、工具调用次数、Token 消耗 | 应用内埋点 |
| API 性能 | 请求延迟 P50/P95/P99、QPS、错误率 | FastAPI 中间件 |
| 知识检索 | 检索延迟、平均相似度、命中率 | SQL 日志 |
| 系统资源 | CPU、内存、磁盘、数据库连接数 | Prometheus + node_exporter |

---

## 第七章 接口设计与系统对接

### 7.1 API 设计原则

重构后的 API 设计遵循以下原则：

1. **向后兼容**：现有前端使用的核心 API 保持路径、请求格式和响应格式不变
2. **RESTful 规范**：资源命名使用名词复数、HTTP 方法语义化、状态码规范化
3. **版本化管理**：所有 API 使用 `/api/v1` 前缀，预留 v2 升级空间
4. **统一响应格式**：所有响应使用 `Result<T>` 封装，保持与原系统一致

**统一响应格式**：
```json
{
    "code": 200,
    "message": "success",
    "data": { ... },
    "timestamp": 1708502400000,
    "requestId": "uuid-xxx"
}
```

### 7.2 API 端点映射（新旧对照）

#### 7.2.1 保持不变的端点

以下端点保持路径和请求/响应格式完全兼容：

| 方法 | 路径 | 功能 | 兼容性 |
|------|------|------|-------|
| POST | /v1/user/login | 用户登录 | ✅ 完全兼容 |
| POST | /v1/user/register | 用户注册 | ✅ 完全兼容 |
| POST | /v1/user/register-with-code | 带验证码注册 | ✅ 完全兼容 |
| POST | /v1/user/send-code | 发送验证码 | ✅ 完全兼容 |
| GET | /v1/user/profile | 获取个人信息 | ✅ 完全兼容 |
| PUT | /v1/user/profile | 更新个人信息 | ✅ 完全兼容 |
| PUT | /v1/user/password | 修改密码 | ✅ 完全兼容 |
| POST | /v1/chat/message | 发送消息（同步） | ✅ 完全兼容 |
| POST | /v1/chat/message/stream | 发送消息（SSE流式） | ✅ 兼容（事件扩展） |
| GET | /v1/chat/conversations | 获取会话列表 | ✅ 完全兼容 |
| POST | /v1/chat/conversations | 创建新会话 | ✅ 完全兼容 |
| GET | /v1/chat/conversations/{id}/messages | 获取会话消息 | ✅ 完全兼容 |
| PUT | /v1/chat/conversations/{id} | 更新会话标题 | ✅ 完全兼容 |
| DELETE | /v1/chat/conversations/{id} | 删除会话 | ✅ 完全兼容 |
| POST | /v1/knowledge/docs | 上传文档 | ✅ 完全兼容 |
| GET | /v1/knowledge/docs | 文档列表 | ✅ 完全兼容 |
| GET | /v1/knowledge/docs/{id} | 文档详情 | ✅ 完全兼容 |
| PUT | /v1/knowledge/docs/{id} | 更新文档 | ✅ 完全兼容 |
| DELETE | /v1/knowledge/docs/{id} | 删除文档 | ✅ 完全兼容 |
| POST | /v1/knowledge/docs/{id}/reindex | 重新索引 | ✅ 完全兼容 |
| GET | /v1/knowledge/categories | 分类列表 | ✅ 完全兼容 |
| GET | /v1/health | 健康检查 | ✅ 完全兼容 |

#### 7.2.2 新增端点

| 方法 | 路径 | 功能 | 说明 |
|------|------|------|------|
| POST | /v1/agent/execute | Agent 执行（SSE流式） | 增强版问答，支持工具调用 |
| GET | /v1/agent/tools | 获取可用工具列表 | 查看 Agent 支持的工具 |
| GET | /v1/agent/history/{conversation_id} | Agent 执行历史 | 查看工具调用轨迹 |
| POST | /v1/internal/campus/* | 内部服务调用代理 | Golang 后端数据中转 |

### 7.3 与 Golang 校园小程序后端对接方案

#### 7.3.1 对接架构

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   前端 (Vue.js)  │     │  Agent (Python)  │     │  Golang 后端     │
│   / 小程序       │────▶│  FastAPI         │────▶│  (校园服务)      │
│                  │ SSE │                  │HTTP │                  │
│                  │◀────│  LangGraph       │◀────│  课程/成绩/通知  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
                                │
                          Agent Tools
                          自动调用Golang API
```

#### 7.3.2 认证授权机制

Agent 服务与 Golang 后端之间的服务间认证采用以下方案：

**方案选型：内部 API Key + JWT 透传**

| 认证层级 | 机制 | 说明 |
|---------|------|------|
| 用户 → Agent | JWT（Bearer Token） | 用户身份认证，24 小时有效 |
| Agent → Golang | 内部 API Key + 用户 JWT 透传 | 服务间认证 + 用户身份传递 |

**请求头设计**：
```http
GET /api/v1/schedule HTTP/1.1
Host: campus-backend.internal
X-Service-Key: {内部API密钥}
X-User-Id: {用户ID}
X-User-Role: {用户角色}
Authorization: Bearer {用户原始JWT}
X-Request-Id: {请求追踪ID}
```

#### 7.3.3 数据交换格式

Agent 与 Golang 后端之间的数据交换采用 JSON 格式，关键数据模型定义：

**课程表数据模型**：
```json
{
    "student_id": "2024001",
    "week": 12,
    "schedule": [
        {
            "day": 1,
            "period": "1-2",
            "course_name": "数据库系统原理",
            "teacher": "张教授",
            "location": "教学楼A-301",
            "time_slot": "08:00-09:40"
        }
    ]
}
```

**通用响应格式**（Golang 后端）：
```json
{
    "code": 0,
    "message": "success",
    "data": { ... }
}
```

**兼容性处理**：Agent 工具层负责将 Golang 后端的响应格式转换为 Agent 内部格式，屏蔽底层差异。

#### 7.3.4 错误处理与降级

| 场景 | 处理策略 |
|------|---------|
| Golang 后端不可达 | 工具返回错误消息，Agent 告知用户"校园服务暂时不可用" |
| 接口响应超时（>10s） | 中断调用，返回超时错误 |
| 数据格式不兼容 | 工具层做容错解析，记录错误日志 |
| 权限不足 | 返回明确的权限不足提示 |

---

## 第八章 技术迁移要点分析

### 8.1 Java 到 Python 的语言迁移

#### 8.1.1 类型系统差异处理

| Java 特性 | Python 对应方案 | 说明 |
|----------|---------------|------|
| 强类型系统 | Pydantic + Type Hints | Python 3.11+ 类型注解 + Pydantic 运行时验证 |
| 接口（Interface） | Protocol / ABC | typing.Protocol（结构性子类型）或 abc.ABC（名义子类型） |
| 泛型（Generics） | TypeVar / Generic | typing 模块泛型支持 |
| 枚举（Enum） | enum.Enum / StrEnum | Python 枚举，StrEnum 用于字符串枚举 |
| Optional 类型 | Optional[T] / T \| None | Python 3.10+ 联合类型语法 |
| Record 类型 | dataclass / NamedTuple | @dataclass(frozen=True) 实现不可变记录 |
| Lombok @Data/@Builder | Pydantic BaseModel | 自动序列化、验证、文档生成 |
| Stream API | 列表推导 / itertools | Python 内置函数式编程支持 |
| Annotation | Decorator | Python 装饰器实现类似 AOP 功能 |

#### 8.1.2 并发模型转换

| Java 模型 | Python 等效 | 适用场景 |
|----------|------------|---------|
| Spring @Async + ThreadPoolExecutor | asyncio + async/await | I/O 密集型操作（API 调用、数据库查询） |
| CompletableFuture | asyncio.Task / asyncio.gather | 并行执行多个异步操作 |
| ConcurrentHashMap | asyncio.Lock + dict | 并发安全的共享状态 |
| ScheduledExecutorService | Celery Beat / APScheduler | 定时任务 |
| SseEmitter | FastAPI StreamingResponse | SSE 流式响应 |
| Semaphore（限流） | asyncio.Semaphore / Redis | 并发限制 |
| Thread-safe Queue | asyncio.Queue | 生产者-消费者模式 |

**关键转换原则**：

- 原 Java 项目的 4 个线程池（chat/document/sse/task）将替换为 asyncio 事件循环 + Celery 任务队列的组合
- 所有 I/O 操作（数据库、HTTP、文件读写）使用 async/await 异步执行
- CPU 密集型操作（如大文件解析）使用 Celery Worker 在独立进程中执行
- FastAPI 的 StreamingResponse 替代 Spring 的 SseEmitter 实现 SSE

#### 8.1.3 异常处理机制调整

| Java 机制 | Python 机制 | 映射关系 |
|----------|------------|---------|
| `@RestControllerAdvice` | FastAPI exception_handler | 全局异常处理 |
| `BusinessException` | 自定义 `AppException(HTTPException)` | 业务异常 |
| `ResultCode` 枚举 | `ErrorCode` 枚举 | 错误码定义 |
| try-catch 层级异常 | try-except 对应处理 | 语义对等 |
| `@Transactional` 回滚 | SQLAlchemy session.rollback() | 事务回滚 |

#### 8.1.4 依赖管理策略

| 维度 | Java (Maven) | Python (Poetry/uv) |
|------|-------------|---------------------|
| 依赖文件 | pom.xml | pyproject.toml |
| 锁文件 | pom.xml (版本锁定) | poetry.lock / uv.lock |
| 虚拟环境 | JVM 全局 | venv / virtualenv |
| 依赖分组 | scope (compile/test/runtime) | dependency groups (dev/test/prod) |
| 版本管理 | Maven Central | PyPI |

**推荐**：使用 `uv`（Rust 编写的 Python 包管理器）替代 Poetry，安装速度快 10-100 倍，已成为 Python 社区新标准。

### 8.2 LangGraph 集成方案

#### 8.2.1 框架核心概念映射

| 原系统概念 | LangGraph 对应概念 | 说明 |
|----------|-------------------|------|
| 传统 RAG 管道 | StateGraph 子图 | 将 RAG 检索流程建模为图的一部分 |
| EnhancedRagService（编排层） | StateGraph（主图） | 编排层职责由 LangGraph 图结构承担 |
| KnowledgeSearchTool（@Tool注解） | @tool 装饰器 + ToolNode | LangGraph 原生工具管理 |
| Tool Calling 手动构建 JSON | LangGraph 自动管理 | 工具调用由框架处理 |
| 手动解析 tool_calls 响应 | ToolNode 自动分发 | 工具结果自动回传 |
| SseEmitter 流式输出 | astream_events() | 细粒度事件流 |
| 会话历史手动管理 | Checkpointer 持久化 | 状态自动保存恢复 |

#### 8.2.2 LangGraph 与各组件的集成

**与 PostgreSQL + pgvector 集成**：
- 使用 `langgraph-checkpoint-postgres` 保存 Agent 检查点到 PG
- 向量检索通过 SQLAlchemy 异步查询 + pgvector 操作符实现
- 知识库 CRUD 操作通过 Repository 模式封装

**与 LLM（DeepSeek）集成**：
- 使用 `langchain-openai` 的 `ChatOpenAI`（DeepSeek API 兼容 OpenAI 格式）
- 配置 `base_url` 为 DeepSeek API 地址
- 支持流式输出和工具调用

**与 Embedding（阿里云百炼）集成**：
- 使用 `langchain-openai` 的 `OpenAIEmbeddings`（兼容 OpenAI 格式）
- 配置 `base_url` 为阿里云百炼 API 地址
- 模型：text-embedding-v3，维度：1024

**与 Redis 集成**：
- 缓存层：热点配置、用户会话
- 限流层：API 速率限制
- 消息中间件：Celery Broker + 文档处理进度通知

#### 8.2.3 状态管理机制

LangGraph 的状态管理替代原系统的手动状态管理：

| 功能 | 原系统实现 | LangGraph 实现 |
|------|----------|---------------|
| 对话历史 | 手动从 DB 加载最近 N 轮消息 | Checkpointer 自动管理 |
| 检索结果缓存 | 方法间参数传递 | State 字段自动流转 |
| 工具调用状态 | 手动维护迭代计数器 | StateGraph 边条件自动控制 |
| 流式中间状态 | ConcurrentHashMap | State 快照 |
| 中断恢复 | 不支持 | Checkpointer 检查点恢复 |

### 8.3 性能优化考量

#### 8.3.1 消除原系统性能瓶颈

| 原系统瓶颈 | 重构后的解决方案 |
|-----------|---------------|
| 重复 Embedding API 调用（技术债务 D-03） | LangGraph State 中缓存查询向量，全流程复用 |
| OkHttpClient 重复创建（D-04） | httpx 全局 AsyncClient，连接池复用 |
| Milvus 删除失败导致不一致（D-05） | pgvector 同一事务删除，强一致性 |
| 缺少缓存层（D-07） | Redis 缓存热点数据 |

#### 8.3.2 Python 性能优化策略

| 优化方向 | 具体措施 |
|---------|---------|
| I/O 并发 | asyncio + 异步数据库 (asyncpg) + 异步 HTTP (httpx) |
| 连接池 | asyncpg 连接池（默认 20 连接）+ Redis 连接池 |
| CPU 密集任务卸载 | Celery Worker 独立进程执行文档解析和向量化 |
| 缓存策略 | 系统配置缓存（TTL 5 分钟）、用户信息缓存（TTL 1 小时） |
| 批量操作 | 向量化批量请求（每批 10 条）、数据库批量插入 |
| 响应压缩 | FastAPI GZip 中间件 |

---

## 第九章 实施计划与里程碑

### 9.1 总体实施策略

采用**分阶段渐进式实施**策略，将整个重构过程划分为 5 个阶段，每个阶段设立明确的交付物和验收标准。

**总工期预估**：8-10 周（假设 2-3 人全职参与）

```
阶段一 (W1-W2)          阶段二 (W3-W4)        阶段三 (W5-W6)        阶段四 (W7-W8)       阶段五 (W9-W10)
基础设施 & 框架搭建      核心模块开发           AI & Agent 集成        系统集成 & 测试       部署 & 迁移上线
├─ 项目脚手架            ├─ 用户认证模块         ├─ LangGraph Agent    ├─ 前后端联调          ├─ 生产环境部署
├─ 数据库建表+迁移        ├─ 对话管理模块         ├─ 工具调用模块        ├─ 集成测试           ├─ 数据迁移
├─ CI/CD 流水线          ├─ 知识库 CRUD          ├─ SSE 流式输出       ├─ 性能测试           ├─ 灰度发布
├─ Docker 环境           ├─ 文档解析器           ├─ Embedding 集成     ├─ 安全测试           ├─ 监控验证
└─ 代码规范 & Lint        └─ 文件上传处理         └─ pgvector 检索      └─ Bug 修复          └─ 旧系统下线
```

### 9.2 各阶段详细计划

#### 阶段一：基础设施与框架搭建（Week 1-2）

| 任务 | 交付物 | 负责人 | 工时 |
|------|-------|-------|------|
| 初始化 Python 项目（uv + FastAPI） | 项目骨架 + pyproject.toml | 后端 | 2h |
| 配置 Pydantic Settings | 分环境配置文件 | 后端 | 2h |
| PostgreSQL 数据库建表 | Alembic 迁移脚本（11 表） | 后端 | 4h |
| pgvector 扩展安装与测试 | 向量查询验证通过 | 后端 | 2h |
| SQLAlchemy 2.0 ORM 模型 | models/ 目录所有 Model | 后端 | 6h |
| Repository 层基础实现 | CRUD 基类 + 分页查询 | 后端 | 4h |
| Docker Compose 编排 | dev/prod 双环境配置 | DevOps | 4h |
| Redis 集成 | 连接池 + 缓存工具类 | 后端 | 2h |
| 日志框架（structlog） | 统一日志格式 + 中间件 | 后端 | 2h |
| 统一异常处理 | exception_handlers + ErrorCode | 后端 | 3h |
| 统一响应格式 | Result 封装 | 后端 | 1h |
| 代码规范工具链 | ruff + mypy + pre-commit | 后端 | 2h |
| GitHub Actions CI | lint + test + build 流水线 | DevOps | 3h |

**阶段一验收标准**：
- [x] FastAPI 服务可在 Docker 中启动
- [x] 数据库表结构与原系统等价
- [x] pgvector 向量相似度查询功能验证
- [x] CI 流水线绿灯

#### 阶段二：核心业务模块开发（Week 3-4）

| 任务 | 交付物 | 前置依赖 | 工时 |
|------|-------|---------|------|
| JWT 认证 + Refresh Token | auth 模块 + 中间件 | 阶段一 | 4h |
| 用户 CRUD + 邮箱验证 | user 模块 | auth | 6h |
| 会话管理 | conversation 模块 | auth | 4h |
| 消息管理 | message 模块 | conversation | 4h |
| 知识文档 CRUD | knowledge 模块 | auth | 6h |
| 知识分类管理 | category 模块 | auth | 2h |
| 文档解析器（7 格式） | parser 模块 | - | 8h |
| 文本切块服务 | splitter 服务 | parser | 4h |
| 文件上传 + 存储 | upload 模块 | - | 3h |
| Celery 异步任务 | task 模块 | Redis | 4h |
| 操作日志 AOP | middleware + decorator | - | 3h |
| 速率限制 | rate_limit 中间件 | Redis | 2h |

**阶段二验收标准**：
- [x] 用户注册/登录/信息管理全流程通过
- [x] 知识文档上传、解析、切块功能正常
- [x] 7 种文件格式解析正确率 > 95%
- [x] API 单元测试覆盖率 > 80%

#### 阶段三：AI 与 Agent 集成（Week 5-6）

| 任务 | 交付物 | 前置依赖 | 工时 |
|------|-------|---------|------|
| LangGraph StateGraph 搭建 | agent 核心图 | - | 8h |
| Embedding 服务（阿里云百炼） | embedding 模块 | - | 4h |
| pgvector 检索服务 | search 服务 | embedding | 6h |
| knowledge_search 工具 | tool 实现 | search | 3h |
| campus_* 工具集 | 6 个工具实现 | - | 6h |
| 工具注册与 ToolNode | 工具管理器 | tools | 3h |
| SSE 流式输出 | streaming 模块 | agent | 6h |
| Agent 检查点持久化 | checkpointer 配置 | PG | 3h |
| DeepSeek LLM 集成 | llm 模块 | - | 3h |
| 系统提示词与人设 | prompt 配置 | llm | 2h |
| 记忆管理（3 层） | memory 模块 | Redis + PG | 4h |

**阶段三验收标准**：
- [x] Agent 能自主决定是否调用工具
- [x] 知识库检索准确率与原系统持平
- [x] SSE 流式输出正常，事件格式兼容
- [x] 工具调用循环次数可控（≤5 次）

#### 阶段四：系统集成与测试（Week 7-8）

| 任务 | 交付物 | 工时 |
|------|-------|------|
| 前后端 API 联调 | 联调通过报告 | 8h |
| Golang 后端对接联调 | 服务间通信验证 | 6h |
| 集成测试编写 | 测试用例 50+ | 8h |
| 性能压测 | JMeter/Locust 压测报告 | 6h |
| 安全审计 | OWASP Top 10 检查 | 4h |
| Bug 修复 & 优化 | Bug 清零 | 10h |
| 文档更新 | API 文档 + 部署文档 | 4h |

**阶段四验收标准**：
- [x] 前端所有功能正常运行
- [x] 响应时间 P95 < 3s（Agent 执行除外）
- [x] 并发 50 用户无错误
- [x] 无高危安全漏洞

#### 阶段五：部署与迁移上线（Week 9-10）

| 任务 | 交付物 | 工时 |
|------|-------|------|
| 生产环境部署 | Docker Compose 部署 | 4h |
| 数据迁移（Milvus → pgvector） | 迁移脚本 + 验证报告 | 6h |
| 用户数据迁移 | 密码/会话/消息数据 | 4h |
| 灰度发布 | 10% → 50% → 100% | 4h |
| 监控告警配置 | Prometheus + Grafana | 4h |
| 旧系统下线 | Milvus + Java 服务关停 | 2h |
| 上线后观察 | 7 天运行日志分析 | 8h |

### 9.3 关键里程碑

| 里程碑 | 时间节点 | 验证方式 |
|-------|---------|---------|
| M1: 框架就绪 | Week 2 末 | FastAPI 服务可启动，DB 建表完成 |
| M2: 核心功能就绪 | Week 4 末 | 用户/知识库 CRUD 全部通过 |
| M3: Agent 能力就绪 | Week 6 末 | Agent 问答 + 工具调用端到端通过 |
| M4: 测试通过 | Week 8 末 | 所有测试绿灯，性能达标 |
| M5: 正式上线 | Week 10 末 | 生产环境稳定运行 7 天 |

---

## 第十章 质量保障与测试策略

### 10.1 测试金字塔

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲          5%  端到端测试（Playwright/Selenium）
                 ╱______╲
                ╱        ╲
               ╱ 集成测试  ╲       20% 模块间集成测试
              ╱____________╲
             ╱              ╲
            ╱   单元测试      ╲    75% 函数/类级别测试
           ╱__________________╲
```

### 10.2 单元测试策略

#### 10.2.1 测试框架选型

| 工具 | 用途 |
|------|------|
| pytest | 测试框架 |
| pytest-asyncio | 异步测试支持 |
| pytest-cov | 覆盖率统计 |
| factory_boy | 测试数据工厂 |
| httpx + ASGI Transport | API 测试客户端 |
| unittest.mock / pytest-mock | Mock 工具 |

#### 10.2.2 覆盖率目标

| 模块 | 最低覆盖率 | 说明 |
|------|----------|------|
| API 路由层 | 90% | 所有端点的正常/异常路径 |
| Service 业务逻辑 | 85% | 核心业务逻辑全覆盖 |
| Repository 数据层 | 80% | CRUD + 复杂查询 |
| Agent 工具 | 90% | 每个工具的输入/输出/异常 |
| 工具函数 | 95% | 纯函数测试 |
| 总体 | ≥ 80% | CI 门禁阈值 |

#### 10.2.3 测试分类与标记

```python
# 使用 pytest markers 分类管理测试
@pytest.mark.unit          # 单元测试，无外部依赖
@pytest.mark.integration   # 集成测试，需要数据库
@pytest.mark.e2e           # 端到端测试
@pytest.mark.slow          # 耗时测试（>5s）
@pytest.mark.agent         # Agent 相关测试
```

### 10.3 集成测试策略

#### 10.3.1 数据库集成测试

使用 Docker 容器化的 PostgreSQL（含 pgvector）进行集成测试：

| 测试场景 | 验证内容 |
|---------|---------|
| ORM 模型映射 | 所有 Model 字段与数据库表列对应正确 |
| CRUD 操作 | 增删改查基本操作正确性 |
| 事务回滚 | 异常情况下事务正确回滚 |
| 向量检索 | pgvector 余弦相似度检索结果准确 |
| 并发写入 | 并发 INSERT 不产生数据竞争 |

#### 10.3.2 Agent 集成测试

| 测试场景 | 方法 | 预期结果 |
|---------|------|---------|
| 知识库问答 | 注入已知文档，提问相关问题 | 回答包含正确知识 |
| 工具调用 | Mock 校园 API，触发工具调用 | 正确调用工具并整合结果 |
| 多轮对话 | 连续发送关联消息 | 上下文正确维持 |
| 超时处理 | 模拟 LLM 超时 | 返回超时错误，不阻塞 |
| 循环保护 | 构造循环调用场景 | ≤5 次迭代后终止 |

### 10.4 性能测试方案

#### 10.4.1 测试工具与环境

| 项目 | 选择 |
|------|------|
| 压测工具 | Locust（Python 编写，与项目技术栈一致） |
| 测试环境 | Docker Compose 单机部署（模拟生产配置） |
| 数据准备 | 100 篇知识文档、1000 个文本块、50 个用户 |

#### 10.4.2 性能指标与基线

| 场景 | 并发用户 | P95 响应时间 | 吞吐量 | 错误率 |
|------|---------|------------|-------|-------|
| 用户登录 | 100 | < 200ms | > 500 req/s | < 0.1% |
| 知识库列表 | 50 | < 300ms | > 200 req/s | < 0.1% |
| 向量检索 | 50 | < 500ms | > 100 req/s | < 0.5% |
| Agent 问答（非流式） | 20 | < 10s | > 10 req/s | < 1% |
| Agent 问答（SSE 流式） | 50 | 首 token < 2s | > 30 conn | < 1% |
| 文档上传+处理 | 10 | < 30s (异步) | > 5 req/s | < 0.5% |

### 10.5 安全测试清单

| 测试项 | 方法 | 通过标准 |
|-------|------|---------|
| SQL 注入 | SQLMap + 手动测试 | 零注入漏洞 |
| XSS 攻击 | Pydantic 输入验证 + CSP 头 | 零 XSS 漏洞 |
| JWT 篡改 | 修改 payload / 过期 Token / 空 Token | 全部拒绝 |
| 越权访问 | 普通用户访问管理员接口 | 返回 403 |
| 文件上传漏洞 | 上传恶意文件 | 类型校验拦截 |
| 速率限制 | 超速请求 | 正确触发限流 429 |
| 敏感数据泄露 | 检查响应和日志 | 无密码/Token 泄露 |
| CORS 配置 | 跨域请求测试 | 仅允许白名单域名 |

### 10.6 代码质量保障

#### 10.6.1 自动化工具链

| 工具 | 作用 | 执行时机 |
|------|------|---------|
| ruff | Linter + Formatter（替代 flake8 + black + isort） | pre-commit + CI |
| mypy | 静态类型检查 | pre-commit + CI |
| bandit | Python 安全漏洞扫描 | CI |
| safety | 依赖安全漏洞检查 | CI (weekly) |
| pytest-cov | 测试覆盖率 | CI（门禁 80%） |
| SonarQube | 综合代码质量 | CI（可选） |

#### 10.6.2 Code Review 规范

| 规则 | 说明 |
|------|------|
| PR 必须至少 1 人 Review | 不允许自行合并 |
| CI 全部通过 | lint + test + type-check 绿灯 |
| 新代码必须有测试 | 新功能需附带测试用例 |
| 类型注解完整 | 所有公开函数必须有类型注解 |
| 文档注释 | 公开 API 需有 docstring |

---

## 第十一章 风险评估与应对方案

### 11.1 风险矩阵

| 风险编号 | 风险描述 | 发生概率 | 影响程度 | 风险等级 | 应对策略 |
|---------|---------|---------|---------|---------|---------|
| R-01 | LangGraph 学习曲线陡峭，团队技能不足 | 高 | 高 | 🔴 高危 | 提前进行技术预研和培训 |
| R-02 | pgvector 检索性能不如 Milvus | 中 | 高 | 🟡 中危 | 性能基准测试 + HNSW 索引调优 |
| R-03 | 数据迁移过程中数据丢失或损坏 | 低 | 高 | 🟡 中危 | 多轮验证 + 保留 Milvus 备份 30 天 |
| R-04 | Python 性能无法满足并发需求 | 中 | 中 | 🟡 中危 | asyncio 架构 + 必要时水平扩展 |
| R-05 | 前后端接口不兼容导致前端改动量大 | 中 | 中 | 🟡 中危 | 严格遵循 API 兼容性设计 |
| R-06 | Golang 后端接口不稳定或变更 | 中 | 低 | 🟢 低危 | 适配层解耦 + 工具降级处理 |
| R-07 | DeepSeek API 不稳定或限流 | 低 | 中 | 🟢 低危 | 重试机制 + 备用 LLM 配置 |
| R-08 | 重构工期超出预期 | 中 | 中 | 🟡 中危 | 分阶段交付 + 敏捷调整 |
| R-09 | 新旧系统切换期间服务中断 | 低 | 高 | 🟡 中危 | 蓝绿部署 + 快速回滚方案 |

### 11.2 高危风险详细应对方案

#### R-01: LangGraph 学习曲线

**缓解措施**：
1. 重构启动前安排 1 周技术预研，完成 LangGraph 官方教程和示例
2. 先用简单的 2 节点图验证核心流程，再逐步扩展
3. 编写项目专属的 LangGraph 开发指南和代码模板
4. 阶段三（Agent 集成）预留 20% 缓冲时间

**触发条件 → 应急方案**：
- 若阶段三进度延迟超过 3 天：简化 Agent 图结构，先实现核心路径
- 若无法实现复杂的条件路由：降级为 LangChain 的 AgentExecutor（功能更少但更易上手）

#### R-02: pgvector 性能风险

**缓解措施**：
1. 阶段一即进行性能基准测试（不等到最后）
2. 使用 HNSW 索引（精度高、查询快），参数优化：
   - `m=16`（连接数）、`ef_construction=64`（构建精度）
   - 查询时 `ef_search=40`（查询精度）
3. 数据量 < 100 万向量时，pgvector 性能完全满足需求

**触发条件 → 应急方案**：
- 若 P95 检索延迟 > 500ms（1000 维、10 万条）：检查索引参数、增加 shared_buffers
- 若性能确实无法满足：考虑引入 Qdrant（与 pgvector 可共存，轻量级方案）

### 11.3 数据安全保障

| 保障措施 | 具体实施 |
|---------|---------|
| 数据库备份 | 生产环境每日全量备份（pg_dump），保留 30 天 |
| WAL 归档 | 启用 PostgreSQL WAL 归档，支持时间点恢复 |
| 迁移前备份 | 数据迁移前完整备份所有表 + Milvus Collection |
| 敏感数据加密 | 密码 BCrypt 哈希，API Key 环境变量存储 |
| 访问控制 | 数据库用户最小权限，禁止 root 直连 |

### 11.4 回滚策略

#### 11.4.1 版本级回滚

| 回滚级别 | 触发条件 | 回滚方式 | RTO |
|---------|---------|---------|------|
| 代码回滚 | 新版本 Bug 率 > 5% | Docker 镜像回退到上一版本 | < 5 分钟 |
| 数据回滚 | 数据损坏或丢失 | pg_restore 从最近备份恢复 | < 30 分钟 |
| 全量回滚 | 新系统不可用 | 切回 Java + Milvus 旧系统 | < 1 小时 |

#### 11.4.2 全量回滚预案

若重构后系统出现严重不可恢复问题，执行以下回滚流程：

```
1. 停止 Python Agent 服务
2. 启动 Java Spring Boot 服务（保留原镜像）
3. 启动 Milvus 服务（数据保留 30 天）
4. Nginx 路由切换回 Java 后端
5. 验证旧系统功能正常
6. 通知用户系统维护完成
```

**前提条件**：
- Java 服务镜像保留不删除（至少 60 天）
- Milvus 数据不清理（至少 30 天）
- PostgreSQL 核心表结构向下兼容（不做破坏性变更）
- Nginx/反向代理支持快速路由切换

### 11.5 上线后监控与告警

| 监控指标 | 告警阈值 | 通知方式 |
|---------|---------|---------|
| API 错误率 | > 5%（5 分钟窗口） | 企业微信/钉钉/邮件 |
| P95 响应时间 | > 5s（非 Agent 接口） | 企业微信/钉钉 |
| Agent 执行超时 | > 30s | 日志记录 + 告警 |
| 数据库连接数 | > 80% 容量 | 邮件告警 |
| 磁盘空间 | > 85% | 邮件告警 |
| Redis 内存 | > 80% | 邮件告警 |
| 向量检索延迟 | P95 > 1s | 日志记录 |

---

## 附录

### 附录 A：技术栈版本清单

| 组件 | 版本 | 说明 |
|------|------|------|
| Python | 3.11+ | LTS 版本，性能优化显著 |
| FastAPI | 0.110+ | 最新稳定版 |
| LangGraph | 0.2+ | Agent 框架 |
| langchain-openai | 0.1+ | LLM/Embedding 集成 |
| SQLAlchemy | 2.0+ | 异步 ORM |
| asyncpg | 0.29+ | 异步 PostgreSQL 驱动 |
| pgvector | 0.7+ | 向量扩展 |
| Redis | 7.0+ | 缓存 + 消息队列 |
| Celery | 5.3+ | 异步任务队列 |
| Pydantic | 2.5+ | 数据验证 |
| httpx | 0.27+ | 异步 HTTP 客户端 |
| structlog | 24.1+ | 结构化日志 |
| Alembic | 1.13+ | 数据库迁移 |
| pytest | 8.0+ | 测试框架 |
| ruff | 0.3+ | Linter + Formatter |
| uv | 0.4+ | 包管理器 |
| Docker | 24+ | 容器化 |
| PostgreSQL | 15+（推荐 16） | 主数据库（兼容现有 PG 15 实例，新部署推荐 PG 16） |

### 附录 B：术语表

| 术语 | 说明 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Agent | 具有自主决策能力的智能体 |
| LangGraph | LangChain 团队开发的有状态 Agent 框架 |
| StateGraph | LangGraph 的核心概念，有状态有限状态机 |
| pgvector | PostgreSQL 的向量扩展，支持向量存储和相似度检索 |
| HNSW | Hierarchical Navigable Small World，高效近似最近邻搜索算法 |
| SSE | Server-Sent Events，服务器推送事件 |
| Tool Calling | LLM 的函数调用能力 |
| Checkpointer | LangGraph 的状态持久化组件 |
| Embedding | 将文本转换为高维向量的过程 |

### 附录 C：参考资料

1. LangGraph 官方文档：https://langchain-ai.github.io/langgraph/
2. FastAPI 官方文档：https://fastapi.tiangolo.com/
3. pgvector 项目主页：https://github.com/pgvector/pgvector
4. DeepSeek API 文档：https://platform.deepseek.com/api-docs
5. 阿里云百炼 Embedding API：https://help.aliyun.com/zh/model-studio/
6. SQLAlchemy 2.0 文档：https://docs.sqlalchemy.org/en/20/
7. Pydantic v2 文档：https://docs.pydantic.dev/latest/
8. uv 包管理器：https://docs.astral.sh/uv/

---

> **文档信息**
> - 文档版本：v1.0.0
> - 创建日期：2026-01-16
> - 最后修订：2026-02-21
> - 文档状态：正式发布
> - 适用范围：EchoCampus-Bot 项目后端重构

