# EchoCampus-Bot 代码质量审查报告 v2

> **文档状态**: 📦 已归档 (2026年1月14日)

**审查日期**: 2026-01-13  
**审查人**: 资深全栈工程师 (Claude Opus 4.5)  
**审查范围**: 全仓库代码审查

---

## 一、总体印象

### 1.1 项目概述

EchoCampus-Bot 是一个基于 **RAG (检索增强生成)** 技术的智能校园问答系统，采用前后端分离架构：

| 层级 | 技术栈 |
|------|--------|
| 后端 | Spring Boot 3.2.1 + MyBatis-Plus + PostgreSQL |
| 向量检索 | Milvus 2.3.4 + LangChain4j |
| 前端 | Vue 3.4 + TypeScript 5.3 + Ant Design Vue |
| 部署 | Docker Compose 多服务编排 |

### 1.2 主要模块

```
├── backend/                    # Spring Boot 后端
│   ├── controller/             # API 控制器 (Chat, Knowledge, User, System)
│   ├── service/                # 业务逻辑层 (Chat, Embedding, Milvus, Document)
│   ├── entity/                 # 数据实体 (User, Conversation, Message, KnowledgeDoc)
│   ├── mapper/                 # MyBatis-Plus 数据访问层
│   ├── config/                 # 配置类 (Security, Milvus, ThreadPool)
│   └── filter/                 # JWT 认证过滤器
├── frontend/                   # Vue 3 前端
│   ├── views/                  # 页面组件 (Chat, Knowledge, Login, Profile)
│   ├── stores/                 # Pinia 状态管理
│   ├── api/                    # API 调用封装
│   └── components/             # 公共组件
└── docs/                       # 项目文档
```

### 1.3 代码组织优缺点

**✅ 优点:**
- 后端采用标准分层架构，Controller/Service/Mapper 职责清晰
- DTO 按 request/response 分离，接口定义清晰
- 统一响应格式 (`Result<T>`) 和错误码枚举 (`ResultCode`)
- 使用 `@RequiredArgsConstructor` 实现构造器注入
- SSE 流式响应实现完整，支持实时聊天进度

**❌ 缺点:**
- 无测试目录和测试代码
- 无 CI/CD 配置
- 前端 ESLint/Prettier 配置缺失
- 部分敏感信息硬编码

---

## 二、架构与模块化

### 2.1 分层架构评估

```
┌─────────────────────────────────────────────────────┐
│                   Controller 层                      │  ✅ 职责清晰
├─────────────────────────────────────────────────────┤
│                    Service 层                        │  ⚠️ 部分方法过长
│  (ChatService, KnowledgeService, MilvusService...)  │
├─────────────────────────────────────────────────────┤
│                    Mapper 层                         │  ✅ MyBatis-Plus 规范
├─────────────────────────────────────────────────────┤
│                    Entity 层                         │  ⚠️ 状态值使用字符串
└─────────────────────────────────────────────────────┘
```

### 2.2 问题清单

| ID | 问题 | 位置 | 严重程度 |
|----|------|------|----------|
| ARCH-01 | Controller 层存在业务逻辑 | `KnowledgeController.java` | 中 |
| ARCH-02 | Service 方法过长 (>150行) | `ChatServiceImpl.sendMessageStream()` | 中 |
| ARCH-03 | 直接注入实现类而非接口 | `KnowledgeController` 注入 `DocumentProgressServiceImpl` | 低 |
| ARCH-04 | 重复的 HTTP 客户端创建 | `AliyunEmbeddingServiceImpl`, `DeepSeekChatServiceImpl` | 中 |

### 2.3 详细说明

#### ARCH-01: Controller 层业务逻辑

**位置**: `backend/src/main/java/com/echocampus/bot/controller/KnowledgeController.java`

```java
// Controller 中构建DTO的业务逻辑，应移至 Service 层
if (progress == null) {
    KnowledgeDoc doc = knowledgeService.getDocumentById(docId);
    if ("COMPLETED".equals(doc.getProcessStatus())) {
        progress = DocumentProgressDTO.completed(...);
    } else if (...) { ... }
}
```

**建议**: 将进度状态判断逻辑移至 `DocumentProgressService.getOrBuildProgress(docId)`

#### ARCH-02: sendMessageStream 方法过长

**位置**: `backend/src/main/java/com/echocampus/bot/service/impl/ChatServiceImpl.java`

该方法约 **188 行**，包含会话创建、消息保存、RAG检索、流式响应等多个职责。

**建议拆分为**:
```java
private Conversation getOrCreateConversation(Long userId, ChatRequest request);
private Message saveUserMessage(Long conversationId, String content);  
private List<KnowledgeChunk> retrieveContext(String question);
private void handleStreamResponse(SseEmitter emitter, ...);
```

---

## 三、代码质量问题

### 3.1 代码重复

| ID | 问题 | 位置 | 修复成本 |
|----|------|------|----------|
| DUP-01 | 向量零值检查重复 | 多个 Service | 低 |
| DUP-02 | 历史消息排序逻辑重复 | `ChatServiceImpl` L68, L162 | 低 |
| DUP-03 | OkHttpClient 重复创建 | `AliyunEmbeddingServiceImpl`, `DeepSeekChatServiceImpl` | 中 |
| DUP-04 | 验证码倒计时逻辑重复 | `Login.vue`, `Profile.vue` | 低 |

#### DUP-01 修复建议

创建工具类:
```java
// backend/src/main/java/com/echocampus/bot/utils/VectorUtil.java
public class VectorUtil {
    public static boolean isZeroVector(float[] vector) {
        if (vector == null) return true;
        for (float v : vector) {
            if (v != 0) return false;
        }
        return true;
    }
}
```

#### DUP-04 修复建议

创建 Vue Composable:
```typescript
// frontend/src/composables/useCountdown.ts
export function useCountdown(duration = 60) {
  const countdown = ref(0)
  let timer: number | null = null

  const start = () => {
    countdown.value = duration
    timer = window.setInterval(() => {
      if (--countdown.value <= 0) stop()
    }, 1000)
  }

  const stop = () => {
    if (timer) clearInterval(timer)
    countdown.value = 0
  }

  onUnmounted(stop)
  return { countdown, start, stop, isActive: computed(() => countdown.value > 0) }
}
```

### 3.2 命名问题

| ID | 问题 | 位置 | 建议 |
|----|------|------|------|
| NAME-01 | Entity 状态使用魔法字符串 | `User.status`, `KnowledgeDoc.processStatus` | 使用枚举类型 |
| NAME-02 | 变量命名不清晰 | `RateLimitConfig.userLimiters` | 改为 `userConcurrentRequestCounters` |
| NAME-03 | 方法命名不够表意 | `UserService.register()` | 改为 `registerWithEmailVerification()` |

#### NAME-01 修复建议

```java
// 创建枚举类
public enum UserStatus { ACTIVE, INACTIVE, LOCKED }
public enum ProcessStatus { PENDING, PROCESSING, COMPLETED, FAILED }
public enum SenderType { USER, BOT, SYSTEM }

// Entity 中使用
@TableField("status")
@EnumValue
private UserStatus status;
```

### 3.3 异常处理问题

| ID | 问题 | 位置 | 严重程度 |
|----|------|------|----------|
| EXC-01 | 异常被吞噬 | `KnowledgeServiceImpl.deleteDocument()` | 高 |
| EXC-02 | 异常信息泄露给用户 | `DeepSeekChatServiceImpl` | 中 |
| EXC-03 | GlobalExceptionHandler 覆盖不全 | 缺少 `AccessDeniedException` 等 | 中 |

#### EXC-01 详情

**位置**: `KnowledgeServiceImpl.java`
```java
// 删除Milvus中的向量
try {
    milvusService.deleteByDocId(docId);
} catch (Exception e) {
    log.warn("删除Milvus向量失败: {}", e.getMessage()); // ❌ 异常被吞噬
}
```

**问题**: 向量删除失败后数据不一致，应记录待重试或回滚事务。

**建议**:
```java
try {
    milvusService.deleteByDocId(docId);
} catch (Exception e) {
    log.error("删除Milvus向量失败，docId={}", docId, e);
    // 方案1: 标记文档为"待清理"状态，后台任务重试
    docMapper.updateStatus(docId, ProcessStatus.CLEANUP_PENDING);
    // 方案2: 抛出异常回滚事务
    throw new VectorCleanupException("向量删除失败", e);
}
```

#### EXC-02 详情

**位置**: `DeepSeekChatServiceImpl.java`
```java
return "抱歉，AI服务请求失败: " + e.getMessage(); // ❌ 泄露内部信息
```

**建议**:
```java
log.error("AI服务请求失败", e);
return "抱歉，AI服务暂时不可用，请稍后再试。";
```

### 3.4 TypeScript 类型问题

| ID | 问题 | 位置 | 建议 |
|----|------|------|------|
| TS-01 | 大量使用 `any` | `api/index.ts`, `stores/chat.ts` | 定义明确类型 |
| TS-02 | 回调函数类型不严格 | `chatApi.streamChat()` 参数 | 使用接口定义 |

**修复示例**:
```typescript
// frontend/src/types/chat.ts
interface SourceDoc {
  docId: number
  title: string
  content: string
  score: number
}

interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

interface StreamCallbacks {
  onContent?: (content: string) => void
  onSources?: (sources: SourceDoc[]) => void
  onDone?: (usage: TokenUsage, responseTimeMs: number) => void
  onError?: (error: Error) => void
}
```

---

## 四、安全问题

### 4.1 高优先级 🔴

| ID | 问题 | 位置 | 风险 | 修复成本 |
|----|------|------|------|----------|
| SEC-01 | 邮件密码明文泄露 | `docker-compose.yml:131` | **严重** | 低 |
| SEC-02 | CORS 允许所有来源 | `SecurityConfig.java:47` | **高** | 低 |
| SEC-03 | 管理接口无权限控制 | `SystemController.triggerCleanup()` | **高** | 低 |
| SEC-04 | 前端硬编码演示密码 | `Login.vue:104` | **中** | 低 |
| SEC-05 | XSS 风险 - v-html | `Chat.vue` | **中** | 中 |

#### SEC-01: 邮件密码泄露

**位置**: `docker-compose.yml` 第 130-131 行
```yaml
MAIL_USERNAME: ${MAIL_USERNAME:-EchoTechStudio@163.com}
MAIL_PASSWORD: ${MAIL_PASSWORD:-ZWw87M2Y3hcmrUSG}  # ❌ 密码已泄露到代码仓库
```

**立即修复**:
1. 从 163 邮箱后台**轮换授权码**
2. 修改 docker-compose.yml:
```yaml
MAIL_USERNAME: ${MAIL_USERNAME:?邮箱用户名必须设置}
MAIL_PASSWORD: ${MAIL_PASSWORD:?邮箱授权码必须设置}
```
3. 确保 `.env` 文件在 `.gitignore` 中

#### SEC-02: CORS 配置过于宽松

**位置**: `backend/src/main/java/com/echocampus/bot/config/SecurityConfig.java:47`
```java
configuration.setAllowedOriginPatterns(List.of("*"));
configuration.setAllowCredentials(true); // ❌ 允许所有来源携带凭证
```

**修复**:
```java
@Value("${cors.allowed-origins:http://localhost:5173}")
private String allowedOrigins;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // 生产环境指定具体域名
    configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowCredentials(true);
    // ...
}
```

#### SEC-03: 管理接口无权限控制

**位置**: `SystemController.java`
```java
@PostMapping("/cleanup")
public Result<Map<String, Object>> triggerCleanup() {
    // ❌ 任何已登录用户都可触发清理任务
```

**修复**:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/cleanup")
public Result<Map<String, Object>> triggerCleanup() { ... }
```

#### SEC-04: 前端硬编码演示密码

**位置**: `frontend/src/views/Login.vue:104`
```typescript
const handleDemoLogin = async () => {
  loginForm.username = 'EchoCampus'
  loginForm.password = 'Echo@2026'  // ❌ 密码硬编码
```

**修复**: 通过后端 API 获取演示凭证，或使用环境变量。

#### SEC-05: XSS 风险

**位置**: `frontend/src/views/Chat.vue`
```vue
<div class="message-text" v-html="renderMarkdown(msg.content)"></div>
```

**修复**:
```bash
pnpm add dompurify @types/dompurify
```
```typescript
import DOMPurify from 'dompurify'

const renderMarkdown = (content: string) => {
  const html = marked(content)
  return DOMPurify.sanitize(html)
}
```

### 4.2 中优先级 🟡

| ID | 问题 | 位置 | 修复成本 |
|----|------|------|----------|
| SEC-06 | JWT 密钥管理不当 | `JwtUtil.java` | 中 |
| SEC-07 | 敏感信息日志输出 | `JwtAuthenticationFilter.java` | 低 |
| SEC-08 | 文件上传检查不完整 | `KnowledgeServiceImpl.java` | 中 |
| SEC-09 | 服务器 IP 硬编码 | `frontend/src/utils/request.ts` | 低 |

### 4.3 低优先级 🟢

| ID | 问题 | 位置 |
|----|------|------|
| SEC-10 | MinIO 使用默认凭据 | `docker-compose.yml` |
| SEC-11 | 缺少 CSRF 防护说明 | `SecurityConfig.java` |

---

## 五、性能与可扩展性

### 5.1 性能问题

| ID | 问题 | 位置 | 影响 | 修复成本 |
|----|------|------|------|----------|
| PERF-01 | 重复向量搜索 | `ChatServiceImpl.retrieveAndBuildContext()` | 高 | 中 |
| PERF-02 | OkHttpClient 重复创建 | 多个 Service | 中 | 低 |
| PERF-03 | 同步阻塞 Thread.sleep | `MilvusServiceImpl.insertBatch()` | 中 | 低 |
| PERF-04 | SSE 限流器泄露风险 | `ChatServiceImpl.java` | 中 | 低 |

#### PERF-01: 重复向量搜索

**位置**: `ChatServiceImpl.java`
```java
// 第一次：检索相关文档
List<KnowledgeChunk> chunks = knowledgeService.search(question, topK);

// 第二次：重新查询获取分数 ❌ 重复执行向量化和搜索
float[] queryVector = embeddingService.embed(question);
List<MilvusService.SearchResult> searchResults = milvusService.search(queryVector, chunks.size(), 0f);
```

**建议**: 首次检索时缓存向量和分数，避免重复计算。

#### PERF-02: OkHttpClient 统一管理

**创建全局配置**:
```java
// backend/src/main/java/com/echocampus/bot/config/HttpClientConfig.java
@Configuration
public class HttpClientConfig {
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }
}
```

### 5.2 数据库优化建议

**建议添加索引** (基于查询模式分析):

```sql
-- conversations 表
CREATE INDEX idx_conv_user_status_updated ON conversations(user_id, status, updated_at DESC);

-- messages 表
CREATE INDEX idx_msg_conv_created ON messages(conversation_id, created_at);

-- knowledge_chunks 表
CREATE INDEX idx_chunk_doc_index ON knowledge_chunks(doc_id, chunk_index);

-- email_verification_codes 表
CREATE INDEX idx_email_code_lookup ON email_verification_codes(email, type, used, expired_at);
```

### 5.3 可扩展性建议

1. **引入消息队列**: 文档处理、向量化等耗时操作使用 RabbitMQ/Kafka 异步处理
2. **Redis 缓存**: 热点问题答案缓存、用户会话缓存
3. **连接池优化**: 当前 Milvus 连接未使用连接池

---

## 六、测试覆盖与质量

### 6.1 现状

| 类型 | 状态 |
|------|------|
| 后端单元测试 | ❌ **完全缺失** - 无 `src/test/` 目录 |
| 后端集成测试 | ❌ 缺失 |
| 前端单元测试 | ❌ 缺失 |
| E2E 测试 | ❌ 缺失 |

### 6.2 优先补测点

| 优先级 | 测试目标 | 原因 |
|--------|----------|------|
| P0 | `JwtUtil` | 安全核心组件 |
| P0 | `UserService.register/login` | 认证流程 |
| P1 | `ChatService.sendMessage` | 核心业务 |
| P1 | `KnowledgeService.search` | RAG 检索 |
| P2 | Controller 层 MockMvc | API 契约验证 |
| P3 | 前端 Pinia Store | 状态管理 |

### 6.3 测试框架建议

**后端**:
```xml
<!-- pom.xml 已包含 spring-boot-starter-test -->
<!-- 建议添加 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

**前端**:
```bash
pnpm add -D vitest @vue/test-utils jsdom
```

---

## 七、CI/CD 与开发体验

### 7.1 CI/CD 现状

| 检查项 | 状态 |
|--------|------|
| GitHub Actions | ❌ 缺失 |
| GitLab CI | ❌ 缺失 |
| 自动化测试 | ❌ 缺失 |
| 代码质量检查 | ❌ 缺失 |

### 7.2 建议添加 CI 配置

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
          
      - name: Build and Test
        run: mvn -B verify --file backend/pom.xml
        
      - name: Upload Coverage
        uses: codecov/codecov-action@v4

  frontend-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 8
          
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'pnpm'
          cache-dependency-path: frontend/pnpm-lock.yaml
          
      - name: Install and Build
        working-directory: frontend
        run: |
          pnpm install
          pnpm lint
          pnpm build
```

### 7.3 代码风格配置

**前端缺失 ESLint 配置**:

`package.json` 定义了 `lint` 脚本，但缺少：
- `eslint.config.js`
- `.prettierrc`
- ESLint 依赖

**建议添加**:
```bash
cd frontend
pnpm add -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin \
  eslint-plugin-vue prettier eslint-config-prettier
```

### 7.4 本地运行步骤 (README 补充)

```bash
# 1. 克隆仓库
git clone https://github.com/xxx/EchoCampus-Bot.git
cd EchoCampus-Bot

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 填入必要的 API Key

# 3. 启动依赖服务
docker-compose up -d postgres milvus-standalone etcd minio

# 4. 启动后端 (需要 JDK 17+)
cd backend
mvn spring-boot:run

# 5. 启动前端 (需要 Node.js 18+ / pnpm)
cd ../frontend
pnpm install
pnpm dev

# 访问
# - 前端: http://localhost:5173
# - API文档: http://localhost:8083/api/doc.html
```

---

## 八、文档与可上手性

### 8.1 文档评估

| 文档 | 状态 | 评价 |
|------|------|------|
| README.md | ✅ 存在 | 内容丰富但缺少 Quick Start |
| 架构图 | ⚠️ 文字描述 | 建议补充 Mermaid/PlantUML 图 |
| API 文档 | ⚠️ 过时 | `API接口设计_旧.yaml` 需更新或删除 |
| 部署文档 | ✅ 存在 | `Docker部署指南.md` 较完整 |
| CONTRIBUTING.md | ❌ 缺失 | 建议添加 |
| LICENSE | ❌ 缺失 | 建议添加 |

### 8.2 代码注释评估

- **优点**: 关键方法有中文注释说明
- **缺点**: 复杂算法缺少详细解释，如 RAG 检索流程

---

## 九、风险与优先级汇总

### 9.1 高优先级 🔴 (建议立即修复)

| ID | 问题 | 影响 | 修复成本 |
|----|------|------|----------|
| SEC-01 | 邮件密码明文泄露 | 账号被盗用 | 低 |
| SEC-02 | CORS 允许所有来源 | CSRF/数据泄露 | 低 |
| SEC-03 | 管理接口无权限控制 | 系统被恶意操作 | 低 |
| SEC-05 | XSS 风险 - v-html | 用户数据泄露 | 中 |

### 9.2 中优先级 🟡 (建议 1-2 周内修复)

| ID | 问题 | 影响 | 修复成本 |
|----|------|------|----------|
| PERF-01 | 重复向量搜索 | 响应延迟、资源浪费 | 中 |
| TEST-01 | 无测试覆盖 | 回归风险高 | 高 |
| CI-01 | 无 CI/CD | 发布质量不可控 | 中 |
| ARCH-02 | Service 方法过长 | 可维护性差 | 中 |

### 9.3 低优先级 🟢 (建议 1 个月内修复)

| ID | 问题 | 影响 | 修复成本 |
|----|------|------|----------|
| DUP-* | 代码重复 | 可维护性 | 低 |
| NAME-* | 命名问题 | 可读性 | 低 |
| DOC-01 | 文档不完整 | 上手困难 | 低 |

---

## 十、改进计划 (可执行)

### Phase 1: 安全修复 (1-2天)

#### 1. 修复密码泄露

```bash
# 1. 立即轮换邮箱授权码 (163邮箱后台操作)

# 2. 修改 docker-compose.yml
```

```yaml
# docker-compose.yml 第 130-131 行
MAIL_USERNAME: ${MAIL_USERNAME:?邮箱用户名必须设置}
MAIL_PASSWORD: ${MAIL_PASSWORD:?邮箱授权码必须设置}
```

#### 2. 修复 CORS 配置

```java
// SecurityConfig.java
@Value("${cors.allowed-origins:http://localhost:5173}")
private String allowedOrigins;

configuration.setAllowedOriginPatterns(
    Arrays.asList(allowedOrigins.split(","))
);
```

#### 3. 添加权限控制

```java
// SystemController.java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/cleanup")
public Result<Map<String, Object>> triggerCleanup() { ... }
```

#### 4. 添加 XSS 防护

```bash
cd frontend
pnpm add dompurify @types/dompurify
```

```typescript
// Chat.vue
import DOMPurify from 'dompurify'

const renderMarkdown = (content: string) => {
  return DOMPurify.sanitize(marked(content) as string)
}
```

### Phase 2: 基础设施 (3-5天)

#### 1. 添加 CI/CD

创建 `.github/workflows/ci.yml` (见第七节)

#### 2. 添加 ESLint 配置

```bash
cd frontend
pnpm add -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-vue
```

#### 3. 优化 Dockerfile (多阶段构建)

```dockerfile
# backend/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/uploads /app/logs && chown -R spring:spring /app
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### Phase 3: 代码质量 (1-2周)

1. 添加核心单元测试 (`JwtUtil`, `UserService`)
2. 重构 `ChatServiceImpl.sendMessageStream()`
3. 提取公共工具类 (`VectorUtil`, `useCountdown`)
4. 状态字符串改为枚举

### Phase 4: 性能优化 (2-4周)

1. 统一 OkHttpClient Bean
2. 优化重复向量搜索
3. 添加数据库索引
4. 引入 Redis 缓存热点数据

---

## 附录: 审查文件清单

### 后端 (已审查)
- `backend/pom.xml`
- `backend/Dockerfile`
- `backend/src/main/java/com/echocampus/bot/**/*.java`
- `backend/src/main/resources/mapper/*.xml`

### 前端 (已审查)
- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/tsconfig.json`
- `frontend/src/**/*.vue`
- `frontend/src/**/*.ts`

### 配置 (已审查)
- `docker-compose.yml`
- `README.md`
- `docs/*.md`

---

**报告完成**  
如有任何问题需要进一步澄清，请联系审查人员。
