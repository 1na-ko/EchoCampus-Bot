<p align="center">
  <img src="docs/assets/logo.png" alt="EchoCampus-Bot Logo" width="50" height="50">
</p>

<h1 align="center">EchoCampus-Bot</h1>

<p align="center">
  <strong>基于 RAG 技术的智能校园知识问答机器人</strong>
</p>

<p align="center">
  简体中文 | <a href="README.en.md">English</a> | <a href="README.ja.md">日本語</a>
</p>

<p align="center">
  <!-- 徽章区域 -->
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.4.0-42b883.svg" alt="Vue.js">
  <img src="https://img.shields.io/badge/Java-17-orange.svg" alt="Java">
  <img src="https://img.shields.io/badge/TypeScript-5.3-blue.svg" alt="TypeScript">
</p>

<p align="center">
  <a href="#-功能特性">功能特性</a> •
  <a href="#-快速开始">快速开始</a> •
  <a href="#-项目演示">项目演示</a> •
  <a href="#-技术架构">技术架构</a> •
  <a href="#-文档">文档</a> •
  <a href="#-贡献指南">贡献指南</a>
</p>

---

## 📖 项目简介

**EchoCampus-Bot** 是一款基于 **RAG (检索增强生成)** 技术的智能校园知识问答机器人。它采用现代化的前后端分离架构，结合 Spring Boot、Vue.js、PostgreSQL 和 Milvus 向量数据库，为校园用户提供准确、智能的知识问答服务。

### 核心亮点

| 特性 | 描述 |
|------|------|
| 🧠 **RAG 智能问答** | 基于检索增强生成技术，结合知识库内容提供精准回答 |
| 📚 **多格式文档支持** | 支持 PDF、Word、PPT、Markdown、TXT 等多种文档格式 |
| 🔍 **语义向量检索** | 使用 Milvus 向量数据库实现高效的语义相似度搜索 |
| ⚡ **智能文本切块** | 基于 LangChain4j 的递归分割，保持语义完整性 |
| 🔐 **完整安全体系** | JWT 认证、邮箱验证、操作日志全覆盖 |
| 🐳 **容器化部署** | 提供完整的 Docker Compose 配置，一键部署 |

### 工作原理

```
用户提问 → 问题向量化 → Milvus检索相关文档 → 构建上下文 → DeepSeek生成答案 → 返回结果
```

---

## ✨ 功能特性

### 💬 智能问答系统

- **RAG 技术架构** - 检索增强生成，提供有据可依的精准回答
- **多轮对话支持** - 保留对话上下文，实现连贯交流
- **来源引用** - 回答附带知识来源，支持溯源验证
- **Markdown 渲染** - 支持代码高亮、表格等富文本展示

### 📚 知识库管理

- **多格式解析** - 支持 PDF、DOCX、DOC、PPT、PPTX、MD、TXT 等格式
- **智能切块** - LangChain4j 递归分割，保持语义完整性
- **分类管理** - 灵活的知识分类体系，便于组织管理
- **批量操作** - 支持批量上传、删除等操作

### 🔐 用户与安全

- **用户认证** - JWT Token 认证机制，安全可靠
- **邮箱验证** - 注册/找回密码邮箱验证码支持
- **角色权限** - 用户/管理员角色区分
- **操作日志** - 完整的操作审计日志记录

### ⚙️ 系统管理

- **配置中心** - 可视化参数配置，无需重启
- **数据统计** - 问答统计、用户统计等数据分析
- **系统监控** - API 响应时间、调用频率监控
- **接口限流** - 内置请求频率限制，防止滥用

### 支持的文档格式

| 格式 | 扩展名 | 解析引擎 | 说明 |
|------|--------|----------|------|
| PDF | `.pdf` | Apache PDFBox 3.0 | 支持文本型 PDF |
| Word | `.docx`, `.doc` | Apache POI 5.2 | 支持 Office 文档 |
| PPT | `.pptx`, `.ppt` | Apache POI 5.2 | 提取幻灯片文本 |
| Markdown | `.md` | Flexmark | 保留结构信息 |
| 纯文本 | `.txt` | Java Native | 通用文本格式 |

---

## 🚀 快速开始

### 环境要求

| 环境 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 后端运行环境 |
| Node.js | 18+ | 前端构建环境 |
| Docker | 20.10+ | 容器化部署（推荐） |
| PostgreSQL | 15+ | 关系型数据库 |
| Milvus | 2.3+ | 向量数据库 |

### Docker 一键部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/yourusername/EchoCampus-Bot.git
cd EchoCampus-Bot

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填入 API 密钥等配置

# 3. 启动所有服务
docker-compose -f docker-compose.dev.yml up -d

# 4. 查看服务状态
docker-compose ps
```

### 手动安装

<details>
<summary>点击展开手动安装步骤</summary>

#### 1️⃣ 启动基础服务

```bash
# 启动 PostgreSQL
docker run -d --name echocampus-postgres \
  -e POSTGRES_DB=echocampus_bot \
  -e POSTGRES_USER=echocampus \
  -e POSTGRES_PASSWORD=your_password \
  -p 5432:5432 postgres:15

# 启动 Milvus（参考官方文档）
# https://milvus.io/docs/install_standalone-docker.md
```

#### 2️⃣ 后端服务

```bash
cd backend

# 配置数据库连接（编辑 application-dev.yml）
# 构建并运行
./mvnw clean package -DskipTests
java -jar target/echocampus-bot-1.0.0.jar --spring.profiles.active=dev
```

#### 3️⃣ 前端服务

```bash
cd frontend

# 安装依赖
pnpm install

# 开发模式运行
pnpm dev

# 生产构建
pnpm build
```

</details>

### 必需的 API 密钥

运行本项目需要配置以下 API 服务：

| 服务 | 用途 | 获取方式 |
|------|------|----------|
| 阿里云百炼 | 文本向量化 (Qwen3-Embedding) | [百炼控制台](https://bailian.console.aliyun.com/) |
| DeepSeek | 大语言模型 | [DeepSeek 平台](https://platform.deepseek.com/) |

在 `.env` 文件中配置：

```bash
# AI 服务配置
ALIYUN_API_KEY=your_aliyun_api_key
DEEPSEEK_API_KEY=your_deepseek_api_key
```

### 基本使用

1. **访问系统**: 打开浏览器访问 `http://localhost:5173`（开发模式）或 `http://localhost`（Docker 部署）
2. **注册账号**: 填写用户名、邮箱完成注册
3. **上传知识**: 进入知识库管理，上传文档
4. **开始对话**: 在聊天界面提问，获取智能回答

---

## 📸 项目演示

> 📌 **提示**: 项目截图即将添加，敬请期待！

<!-- 
<p align="center">
  <img src="docs/assets/demo-chat.png" alt="聊天界面" width="80%">
  <br><em>智能问答聊天界面</em>
</p>

<p align="center">
  <img src="docs/assets/demo-knowledge.png" alt="知识库管理" width="80%">
  <br><em>知识库文档管理</em>
</p>
-->

---

## 🏗️ 技术架构

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                          用户界面 (Vue.js 3)                         │
│     聊天交互  │  知识库管理  │  用户中心  │  系统设置                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ RESTful API
┌──────────────────────────────▼──────────────────────────────────────┐
│                       后端服务 (Spring Boot 3)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ 问答服务  │  │ 知识服务  │  │ 用户服务  │  │ 系统服务  │            │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
└───────┼─────────────┼─────────────┼─────────────┼───────────────────┘
        │             │             │             │
┌───────▼─────────────▼─────────────▼─────────────▼───────────────────┐
│                           数据存储层                                 │
│  ┌─────────────────┐              ┌─────────────────┐               │
│  │   PostgreSQL    │              │     Milvus      │               │
│  │  • 用户数据      │              │  • 文档向量      │               │
│  │  • 对话历史      │              │  • 语义索引      │               │
│  │  • 知识元数据    │              │                 │               │
│  └─────────────────┘              └─────────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                          AI 服务层                                   │
│  ┌─────────────────────┐      ┌─────────────────────┐              │
│  │  阿里云百炼平台       │      │     DeepSeek        │              │
│  │  Qwen3-Embedding    │      │   deepseek-chat     │              │
│  │  文本向量化 (1024维) │      │   答案生成          │              │
│  └─────────────────────┘      └─────────────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### 技术栈

<table>
  <tr>
    <th align="center">🖥️ 前端</th>
    <th align="center">⚙️ 后端</th>
    <th align="center">💾 数据存储</th>
    <th align="center">🤖 AI 服务</th>
  </tr>
  <tr>
    <td>
      <img src="https://img.shields.io/badge/Vue.js-3.4-42b883?logo=vue.js" alt="Vue.js"><br>
      <img src="https://img.shields.io/badge/TypeScript-5.3-3178c6?logo=typescript" alt="TypeScript"><br>
      <img src="https://img.shields.io/badge/Vite-5.0-646cff?logo=vite" alt="Vite"><br>
      <img src="https://img.shields.io/badge/Ant_Design-4.1-0170fe?logo=antdesign" alt="Ant Design"><br>
      <img src="https://img.shields.io/badge/Pinia-2.1-ffd859" alt="Pinia">
    </td>
    <td>
      <img src="https://img.shields.io/badge/Spring_Boot-3.2-6db33f?logo=springboot" alt="Spring Boot"><br>
      <img src="https://img.shields.io/badge/MyBatis_Plus-3.5-blue" alt="MyBatis Plus"><br>
      <img src="https://img.shields.io/badge/LangChain4j-0.28-orange" alt="LangChain4j"><br>
      <img src="https://img.shields.io/badge/Knife4j-4.4-green" alt="Knife4j"><br>
      <img src="https://img.shields.io/badge/JWT-0.12-purple" alt="JWT">
    </td>
    <td>
      <img src="https://img.shields.io/badge/PostgreSQL-15-336791?logo=postgresql" alt="PostgreSQL"><br>
      <img src="https://img.shields.io/badge/Milvus-2.3-00a1ea" alt="Milvus"><br>
      <img src="https://img.shields.io/badge/Druid-1.2-blue" alt="Druid">
    </td>
    <td>
      <img src="https://img.shields.io/badge/Qwen3_Embedding-1024d-ff6600" alt="Qwen3"><br>
      <img src="https://img.shields.io/badge/DeepSeek-Chat-7c3aed" alt="DeepSeek">
    </td>
  </tr>
</table>

---

## 📂 项目结构

```
EchoCampus-Bot/
├── 📁 backend/                    # 后端 Spring Boot 项目
│   ├── src/main/java/            # Java 源代码
│   │   └── com/echocampus/bot/
│   │       ├── config/           # 配置类 (Security, AI, Milvus等)
│   │       ├── controller/       # RESTful 控制器
│   │       ├── service/          # 业务服务层
│   │       ├── mapper/           # MyBatis 数据访问层
│   │       ├── entity/           # 实体类
│   │       ├── dto/              # 数据传输对象
│   │       ├── parser/           # 文档解析器
│   │       └── utils/            # 工具类
│   ├── src/main/resources/       # 配置文件
│   └── pom.xml                   # Maven 配置
│
├── 📁 frontend/                   # 前端 Vue.js 项目
│   ├── src/
│   │   ├── api/                  # API 接口封装
│   │   ├── components/           # 公共组件
│   │   ├── views/                # 页面视图
│   │   ├── stores/               # Pinia 状态管理
│   │   ├── router/               # 路由配置
│   │   └── utils/                # 工具函数
│   ├── package.json              # 依赖配置
│   └── vite.config.ts            # Vite 构建配置
│
├── 📁 docs/                       # 项目文档
│   ├── 项目结构设计书.md          # 完整技术架构文档
│   ├── deployment/               # 部署文档
│   ├── development/              # 开发文档
│   └── reference/                # 参考资料
│
├── 🐳 docker-compose.dev.yml     # 开发环境 Docker 配置
├── 🐳 docker-compose.prod.yml    # 生产环境 Docker 配置
├── 📄 .env.example               # 环境变量模板
└── 📄 README.md                  # 项目说明 (本文件)
```

> 📖 **详细结构说明**: 请参阅 [项目结构设计书](docs/项目结构设计书.md)

---

## 📚 文档

| 文档 | 描述 |
|------|------|
| 📐 [项目结构设计书](docs/项目结构设计书.md) | 完整的系统架构、数据库设计、API 规范 |
| 🚀 [快速部署指南](docs/deployment/快速部署指南.md) | 本地开发和生产环境部署说明 |
| ⚙️ [环境变量配置](docs/deployment/环境变量配置说明.md) | 环境变量详细说明 |
| 🧪 [测试代码说明](docs/development/测试代码说明.md) | 测试架构与运行指南 |
| 🔍 [知识库检索增强](docs/reference/知识库检索增强功能说明.md) | RAG 技术实现说明 |

### API 文档

启动后端服务后，访问 Swagger 文档：
- 开发环境: `http://localhost:8083/doc.html`
- 生产环境: `http://your-domain/api/doc.html`

---

## 🤝 贡献指南

我们欢迎任何形式的贡献！无论是报告问题、提出建议还是提交代码。

### 如何贡献

1. **Fork** 本仓库
2. **创建** 特性分支 (`git checkout -b feature/AmazingFeature`)
3. **提交** 更改 (`git commit -m 'Add some AmazingFeature'`)
4. **推送** 到分支 (`git push origin feature/AmazingFeature`)
5. **提交** Pull Request

### 代码规范

- 后端遵循 [阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)
- 前端遵循 [Vue.js 风格指南](https://vuejs.org/style-guide/)
- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)

### 提交 Issue

- 🐛 **Bug 报告**: 请使用 Bug 报告模板，提供详细的复现步骤
- 💡 **功能建议**: 请描述您期望的功能和使用场景
- 📖 **文档改进**: 欢迎指出文档中的错误或不清晰之处

---

## 📋 开发路线图

- [x] 🧠 RAG 智能问答核心功能
- [x] 📚 多格式文档解析与向量化
- [x] 🔐 用户认证与权限管理
- [x] 🐳 Docker 容器化部署
- [x] 📧 邮箱验证码功能
- [ ] 🔄 流式响应 (SSE)
- [ ] 🌐 多语言支持
- [ ] 📱 移动端适配
- [ ] 🔌 插件系统
- [ ] 📊 高级数据分析仪表板

> 💬 有新功能建议？欢迎 [提交 Issue](../../issues/new)！

---

## 💬 社区支持

如果您在使用过程中遇到问题，可以通过以下方式获取帮助：

- 📖 查阅 [项目文档](docs/README.md)
- 🔍 搜索 [已有 Issues](../../issues)
- ❓ 提交 [新 Issue](../../issues/new)
- 💬 参与 [Discussions](../../discussions)

---

## 📄 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 开源。

GPL-3.0 是一个强 Copyleft 许可证，要求衍生作品也必须以相同的许可证开源。

---

## 🙏 致谢

感谢以下开源项目和服务：

- [Spring Boot](https://spring.io/projects/spring-boot) - 后端框架
- [Vue.js](https://vuejs.org/) - 前端框架
- [LangChain4j](https://docs.langchain4j.dev/) - LLM 应用框架
- [Milvus](https://milvus.io/) - 向量数据库
- [Apache PDFBox](https://pdfbox.apache.org/) - PDF 解析
- [Apache POI](https://poi.apache.org/) - Office 文档解析
- [阿里云百炼](https://bailian.console.aliyun.com/) - Embedding 服务
- [DeepSeek](https://www.deepseek.com/) - LLM 服务

---

<p align="center">
  如果这个项目对您有帮助，请给我们一个 ⭐ Star！
</p>

<p align="center">
  Made with ❤️ by EchoCampus Team from Shanghai Institute of Technology
</p>
