# EchoCampus-Bot 测试套件编写指南

## 一、项目上下文

**项目名称**: EchoCampus-Bot（高校知识库AI对话机器人）

**技术栈**：
- 后端：Spring Boot 3.x + MyBatis + PostgreSQL + JWT
- 前端：Vue 3 + TypeScript + Pinia + Vite

**测试框架**：
- 后端：JUnit 5 + Mockito + TestContainers (PostgreSQL)
- 前端：Vitest + @vue/test-utils + jsdom

---

## 二、测试优先级与目标

### 🔴 P0（最高优先级 - 必须完成）

#### 1. JwtUtil - 安全核心组件
**位置**: `src/main/java/com/echocampus/bot/utils/JwtUtil.java`

**测试目标**：
- ✅ Token 生成与签名验证
- ✅ Token 过期检测
- ✅ Claim 提取正确性（userId, username, roles）
- ✅ 无效 Token 处理
- ✅ Token 篡改检测
- ✅ 时间边界测试（刚好过期、刚生成）

**关键断言**：
```
expect(jwtUtil.validateToken(token)).isTrue()
expect(jwtUtil.extractUserId(token)).isEqualTo(123L)
expect(jwtUtil.validateToken(expiredToken)).isFalse()
expect(() -> jwtUtil.validateToken(tamperedToken)).throwsException()
```

#### 2. UserService - 认证流程核心
**位置**: `src/main/java/com/echocampus/bot/service/UserService.java`

**测试方法**：
- `registerUser()` - 注册功能
  - 正常注册：保存用户，生成正确的 DTO
  - 重复用户名：抛出 BusinessException
  - 无效邮箱：验证输入
  - 密码加密验证：确保使用 BCryptPasswordEncoder

- `loginUser()` - 登录功能
  - 正确凭证：返回有效 Token
  - 错误密码：抛出异常
  - 用户不存在：抛出异常
  - 账户被禁用：拒绝登录
  - Token 格式验证

**Mock 策略**：
```
@Mock UserMapper userMapper
@Mock JwtUtil jwtUtil
@Mock PasswordEncoder passwordEncoder
@InjectMocks UserService userService
```

---

### 🟡 P1（高优先级 - 核心业务）

#### 3. ChatService.sendMessage - 对话核心
**位置**: `src/main/java/com/echocampus/bot/service/ChatService.java`

**测试覆盖**：
- 正常消息发送与保存
- 知识库检索集成
- 大模型调用模拟
- 消息去重（相同内容快速重复）
- 上下文管理（对话历史加载）
- 异常处理（API 超时、模型错误）
- 消息长度限制

**测试数据**：
```java
Conversation conversation = new Conversation();
conversation.setId(1L);
conversation.setUserId(100L);
conversation.setCreateTime(now());

Message input = new Message("查询数据库设计");
```

#### 4. KnowledgeService.search - RAG 检索
**位置**: `src/main/java/com/echocampus/bot/service/KnowledgeService.java`

**测试目标**：
- 全文搜索准确性（关键词匹配）
- 向量相似度搜索
- 分页功能
- 类别过滤
- 结果排序（相关度降序）
- 边界情况（空查询、无结果、超大结果集）

---

### 🟢 P2（中优先级 - API 层）

#### 5. Controller 层 - MockMvc API 契约验证
**位置**: `src/main/java/com/echocampus/bot/controller/`

**测试对象**：
- UserController (登录、注册、获取用户信息)
- ChatController (发送消息、获取对话)
- KnowledgeController (搜索、上传)

**每个接口验证**：
```
请求路径、HTTP 方法 ✅
请求参数校验（@Validated）✅
响应状态码（200, 400, 401, 404）✅
响应体结构与类型 ✅
错误消息格式 ✅
权限认证（@RequiresAuth）✅
```

**MockMvc 示例**：
```java
mockMvc.perform(post("/api/user/login")
    .contentType(APPLICATION_JSON)
    .content(json))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value(200))
    .andExpect(jsonPath("$.data.token").exists());
```

---

### 🔵 P3（低优先级 - 前端状态）

#### 6. Pinia Store - 状态管理
**位置**: `frontend/src/stores/`

**测试对象**：
- `chat.ts` - 对话状态管理
- `user.ts` - 用户认证状态
- `knowledge.ts` - 知识库状态

**Vitest 测试**：
```typescript
describe('Chat Store', () => {
  it('should add message to conversation', () => {
    // 初始化
    // 调用 action
    // 断言状态变化
  })
  
  it('should clear conversation', () => {
    // 测试清空对话
  })
})
```

---

## 三、后端测试编写规范

### 3.1 JUnit 5 + Mockito 单元测试模板

```java
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("应该成功生成和验证有效的 JWT Token")
    void testGenerateAndValidateToken() {
        // Arrange
        String userId = "user123";
        
        // Act
        String token = jwtUtil.generateToken(userId);
        boolean isValid = jwtUtil.validateToken(token);
        
        // Assert
        assertThat(isValid).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("过期的 Token 应该验证失败")
    void testExpiredTokenValidation() {
        // 使用 @MockedStatic 或 Clock 来控制时间
        // Arrange & Act & Assert
    }
}
```

### 3.2 TestContainers 集成测试（数据库）

```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test_echocampus")
        .withUsername("test")
        .withPassword("test");

    @Test
    void testUserRegistrationAndRetrieval() {
        // 测试真实数据库操作
    }
}
```

### 3.3 MockMvc 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void testLoginEndpoint() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("user@test.com", "password123");
        when(userService.login(any())).thenReturn(new LoginResponse("token123"));
        
        // Act & Assert
        mockMvc.perform(post("/api/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value("token123"));
    }
}
```

---

## 四、前端测试编写规范

### 4.1 Vitest + @vue/test-utils 模板

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

describe('Chat Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should add a new message', () => {
    const store = useChatStore()
    
    store.addMessage({
      id: 1,
      content: 'Hello',
      role: 'user',
      timestamp: Date.now()
    })
    
    expect(store.messages).toHaveLength(1)
    expect(store.messages[0].content).toBe('Hello')
  })

  it('should clear all messages', () => {
    const store = useChatStore()
    store.addMessage({ /* ... */ })
    
    store.clearConversation()
    
    expect(store.messages).toHaveLength(0)
  })
})
```

### 4.2 异步操作测试（API 调用）

```typescript
it('should fetch messages from API', async () => {
  const store = useChatStore()
  
  vi.mock('@/api', () => ({
    getConversation: vi.fn().mockResolvedValue({
      data: { messages: [...] }
    })
  }))
  
  await store.fetchConversation(1)
  
  expect(store.messages).toHaveLength(expectedCount)
  expect(store.loading).toBe(false)
})
```

---

## 五、具体编写步骤

### 第一阶段：P0 测试（第 1 周）
```
Day 1-2: JwtUtil 单元测试 (15+ cases)
Day 3-4: UserService 单元测试 (20+ cases)
Day 5: 代码审查与调整
```

### 第二阶段：P1 测试（第 2 周）
```
Day 1-2: ChatService 单元测试 (18+ cases)
Day 3-4: KnowledgeService 单元测试 (16+ cases)
Day 5: 集成测试调整
```

### 第三阶段：P2-P3 测试（第 3-4 周）
```
Controller MockMvc 测试
前端 Store 测试
端到端集成验证
```

---

## 六、测试文件组织结构

```
backend/src/test/java/com/echocampus/bot/
├── utils/
│   └── JwtUtilTest.java
├── service/
│   ├── UserServiceTest.java
│   ├── UserServiceIntegrationTest.java
│   ├── ChatServiceTest.java
│   └── KnowledgeServiceTest.java
├── controller/
│   ├── UserControllerTest.java
│   ├── ChatControllerTest.java
│   └── KnowledgeControllerTest.java
└── config/
    └── TestContainersConfig.java

frontend/src/__tests__/
├── stores/
│   ├── chat.test.ts
│   ├── user.test.ts
│   └── knowledge.test.ts
└── api/
    └── request.test.ts
```

---

## 七、测试覆盖率目标

```
单元测试覆盖率：
- 工具类 (Utils)：> 90%
- Service 层：> 85%
- Controller 层：> 80%
- Entity/DTO：> 70%

集成测试：
- 关键业务流程：100%
- 数据库操作：> 90%
```

---

## 八、质量保障检查清单

- [ ] 所有 P0 测试通过率 100%
- [ ] 所有 P1 测试通过率 > 95%
- [ ] 测试代码无重复（使用 @ParameterizedTest）
- [ ] 异常场景覆盖 > 80%
- [ ] 边界值测试完整
- [ ] 测试文档完整（DisplayName、JavaDoc）
- [ ] 代码覆盖率报告生成（JaCoCo）
- [ ] 前端测试通过率 > 90%

---

## 九、快速命令

```bash
# 运行后端所有测试
mvn test

# 生成覆盖率报告
mvn clean test jacoco:report

# 前端测试
cd frontend && pnpm test

# 查看覆盖率
open backend/target/site/jacoco/index.html
```

---

## 十、常见错误避免

❌ **不要**: 测试依赖数据库、外部 API、时间等不确定因素
✅ **要**: 使用 Mock、Stub、TestContainers 隔离依赖

❌ **不要**: 单个测试方法超过 50 行
✅ **要**: 单个测试 Arrange-Act-Assert 清晰，15-30 行

❌ **不要**: 测试用 `assertTrue(result)` 不说明问题
✅ **要**: 使用 AssertJ: `assertThat(result).isEqualTo(expected)`

---

## 十一、AI 辅助编写提示

当让 AI 生成测试时，提供以下信息：

1. **源代码**：粘贴要测试的完整方法
2. **依赖关系**：列出所有 @Autowired/@Inject 对象
3. **业务规则**：解释该方法的 3-5 个核心业务规则
4. **异常情况**：列出应该抛出的异常及原因
5. **现有测试**：参考项目已有的测试风格
6. **覆盖需求**：明确要测试哪些路径

**示例提示**：
```
请为以下 UserService.register() 方法生成 JUnit 5 单元测试：
[粘贴方法代码]

Mock 对象：UserMapper, PasswordEncoder, JwtUtil
核心规则：
- 用户名唯一性检查
- 密码必须加密
- 返回成功注册的用户 DTO

异常：DuplicateUserException, ValidationException
参考风格：使用 AssertJ, @DisplayName, Arrange-Act-Assert
```

---

## 十二、推荐使用流程

### 快速生成 P0 JwtUtil 测试
```
复制下面提示给 AI：

我有一个 Spring Boot 项目 EchoCampus-Bot，需要为 JwtUtil 类编写 JUnit 5 测试。
请参考《测试编写AI提示词指南》中的 P0 - 1. JwtUtil 部分。

这是我的源代码：
[粘贴你的 JwtUtil.java 完整代码]

请生成：
- 15+ 个测试用例
- 包含正常场景、异常场景、边界值
- 使用 @DisplayName 中文说明
- 使用 AssertJ 断言
- 使用 @ParameterizedTest 测试多个 Token 过期时间
```

### 快速生成 P1 ChatService 测试
```
复制下面提示给 AI：

我需要为 ChatService.sendMessage() 方法编写 JUnit 5 + Mockito 测试。
请参考《测试编写AI提示词指南》中的 P1 - 3. ChatService.sendMessage 部分。

这是源代码：
[粘贴你的 ChatService.java 中 sendMessage 方法]

依赖对象：
- @Autowired KnowledgeService knowledgeService
- @Autowired MessageMapper messageMapper
- @Autowired LlmService llmService

关键业务规则：
1. 保存用户消息到数据库
2. 调用知识库搜索获取相关文档
3. 传递给大模型生成回答
4. 保存 AI 回复
5. 返回完整对话响应

异常场景：
- 知识库搜索超时 → 返回默认回答
- 大模型 API 失败 → 抛出 LlmException
- 消息内容为空 → 抛出 ValidationException

生成格式：Arrange-Act-Assert，18+ 个测试用例
```

### 快速生成前端 Store 测试
```
复制下面提示给 AI：

我需要为 Vue 3 + Pinia 项目编写 Vitest 测试。
请参考《测试编写AI提示词指南》中的 P3 - 6. Pinia Store 部分。

Store 代码：
[粘贴你的 src/stores/chat.ts]

生成要求：
- 使用 Vitest + @vue/test-utils
- 每个 action 2-3 个测试用例
- 包含状态验证、异步操作、错误处理
- 使用 vi.mock() 模拟 API 调用
- 测试文件输出到 src/__tests__/stores/chat.test.ts
```

---

## 十三、总结

这份指南提供了：
✅ 按优先级的测试目标（P0-P3）
✅ 后端/前端的代码模板
✅ TestContainers 数据库隔离
✅ MockMvc API 验证
✅ Vitest 状态管理测试
✅ 质量检查清单
✅ 常见错误避免
✅ AI 助手使用技巧

**预期产出**：
- P0：15+18 = 33 个高关键测试 ✅
- P1：18+16 = 34 个核心业务测试 ✅
- P2：30+ 个 Controller API 测试 ✅
- P3：20+ 个前端状态测试 ✅
- **总计：120+ 个测试，覆盖率 > 80%** ✅

---

**更新时间**: 2026-01-13
**版本**: v1.0
