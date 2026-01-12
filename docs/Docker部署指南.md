# EchoCampus-Bot Docker部署指南

## 📋 前置要求

- Docker Desktop (Windows/Mac) 或 Docker Engine (Linux)
- Docker Compose
- JDK 17
- Maven 3.6+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

## 🚀 部署方式

本指南提供两种部署方式：
1. **本地部署测试**：在本地环境快速启动所有服务进行开发和测试
2. **线上部署**：将应用部署到生产服务器

---

## 📦 本地部署测试

### 步骤1：配置环境变量

```bash
# 复制示例配置文件
cp .env.example .env

# 编辑.env文件，填入你的API密钥
nano .env  # Linux/Mac
notepad .env  # Windows
```

必需配置项：
```bash
# PostgreSQL数据库配置
POSTGRES_DB=echocampus_bot
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123
POSTGRES_PORT=5432

# 后端服务端口
BACKEND_PORT=8083

# 阿里云百炼平台API密钥（Embedding服务）
ALIYUN_API_KEY=your_aliyun_api_key_here

# DeepSeek API密钥（LLM服务）
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# JWT密钥（建议修改为随机字符串）
JWT_SECRET=EchoCampusBotSecretKey2024VeryLongAndSecureKeyForJWTToken
```

### 步骤2：构建后端应用

```bash
cd backend
mvn clean package -DskipTests
cd ..
```

构建成功后，会在 `backend/target/` 目录生成 `echocampus-bot-1.0.0.jar` 文件。

### 步骤3：启动所有服务

```bash
docker-compose up -d --build
```

此命令会：
- 构建后端 Docker 镜像
- 启动 PostgreSQL、Milvus、etcd、MinIO、Attu 等依赖服务
- 启动后端服务

### 步骤4：验证服务状态

```bash
# 查看所有容器状态
docker-compose ps

# 查看后端服务日志
docker-compose logs -f echocampus-bot

# 测试健康检查接口
curl http://localhost:8083/api/v1/health
```

### 步骤5：访问服务

- **后端API**: http://localhost:8083/api
- **API文档**: http://localhost:8083/api/doc.html
- **Milvus管理界面**: http://localhost:8000
- **MinIO控制台**: http://localhost:9001 (用户名/密码: minioadmin)

---

## 🌐 线上部署

线上部署需要将镜像推送到镜像仓库，然后在服务器上拉取运行。

### 步骤1：本地构建和打包

```bash
# 进入后端目录
cd backend

# Maven打包（跳过测试）
mvn clean package -DskipTests

# 返回项目根目录
cd ..
```

### 步骤2：构建 Docker 镜像

```bash
docker build -t echocampus:latest ./backend
```

### 步骤3：登录镜像仓库

以阿里云镜像仓库为例：

```bash
echo "<阿里云密码>" | docker login --username=<阿里云用户名> --password-stdin <阿里云镜像仓库地址>
```

### 步骤4：标记镜像

```bash
docker tag echocampus:latest <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
```

### 步骤5：推送镜像到仓库

```bash
docker push <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
```

### 步骤6：连接服务器

```bash
ssh <用户名>@<服务器IP>
```

### 步骤7：服务器上登录镜像仓库

```bash
echo "<阿里云密码>" | docker login --username=<阿里云用户名> --password-stdin <阿里云镜像仓库地址>
```

### 步骤8：拉取最新镜像

```bash
docker pull <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
```

### 步骤9：配置服务器环境变量

在服务器上创建 `.env` 文件：

```bash
cd /home/<用户名>/docker-projects
nano .env
```

配置内容与本地部署相同，注意修改生产环境的密码和密钥。

### 步骤10：修改 docker-compose.yml

将 `echocampus-bot` 服务的 `build` 配置改为 `image`：

```yaml
echocampus-bot:
  container_name: echocampus-bot
  image: <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
  environment:
    # ... 其他配置保持不变
```

### 步骤11：使用 docker-compose 重启服务

```bash
cd /home/<用户名>/docker-projects

# 停止所有服务
docker compose down

# 启动所有服务
docker compose up -d
```

### 步骤12：验证服务状态

```bash
# 查看容器状态
docker ps | grep echocampus

# 测试健康检查接口
curl http://localhost:<后端端口>/api/v1/health

# 查看后端日志
docker logs echocampus-bot --tail 30
```

## 📁 项目结构

```
EchoCampus-Bot/
├── backend/                    # 后端项目
│   ├── Dockerfile             # 后端Docker构建文件
│   ├── .dockerignore          # Docker忽略文件
│   └── src/main/resources/
│       ├── application.yml    # 主配置文件
│       ├── application-docker.yml  # Docker环境配置
│       └── application-local.yml  # 本地开发配置
├── frontend/                   # 前端项目（需单独部署）
├── docs/
│   └── 数据库设计.sql         # 数据库初始化脚本
├── docker-compose.yml          # Docker Compose配置
├── .env                        # 环境变量配置
├── .env.example                # 环境变量示例
├── deploy.sh                   # Linux/Mac部署脚本
└── deploy.bat                  # Windows部署脚本
```

## 🔧 服务说明

部署后包含以下服务：

| 服务名 | 容器名 | 端口 | 说明 |
|--------|--------|------|------|
| PostgreSQL | echocampus-postgres | 5432 | 关系型数据库 |
| Milvus | milvus-standalone | 19530, 9091 | 向量数据库 |
| etcd | milvus-etcd | - | Milvus元数据存储 |
| MinIO | milvus-minio | 9000, 9001 | 对象存储 |
| Attu | milvus-attu | 8000 | Milvus管理界面 |
| EchoCampus-Bot | echocampus-bot | 8083 | 后端服务 |

### 服务依赖关系

```
echocampus-bot (后端服务)
    ├── postgres (PostgreSQL数据库)
    └── milvus-standalone (Milvus向量数据库)
            ├── etcd (元数据存储)
            └── minio (对象存储)
```

**重要**：后端服务依赖 PostgreSQL 和 Milvus，必须通过 docker-compose 启动所有服务，不能单独启动后端容器。

## 🔐 环境变量配置

编辑 `.env` 文件，配置以下变量：

```bash
# PostgreSQL数据库配置
POSTGRES_DB=echocampus_bot
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123
POSTGRES_PORT=5432

# 后端服务端口
BACKEND_PORT=8083

# 阿里云百炼平台API密钥（Embedding服务）
ALIYUN_API_KEY=your_aliyun_api_key_here

# DeepSeek API密钥（LLM服务）
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# JWT密钥（建议修改为随机字符串）
JWT_SECRET=EchoCampusBotSecretKey2024VeryLongAndSecureKeyForJWTToken
```

### 生产环境安全建议

1. 修改默认密码：`POSTGRES_PASSWORD`
2. 使用强随机字符串作为 `JWT_SECRET`
3. 不要将 `.env` 文件提交到版本控制
4. 定期轮换 API 密钥

## 🌐 访问地址

部署成功后，可以通过以下地址访问服务：

- **后端API**: http://localhost:8083/api
- **API文档**: http://localhost:8083/api/doc.html
- **健康检查**: http://localhost:8083/api/v1/health
- **Milvus管理界面**: http://localhost:8000
- **MinIO控制台**: http://localhost:9001 (用户名/密码: minioadmin)

## 📊 常用命令

### 查看服务状态
```bash
docker-compose ps
```

### 查看日志
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f echocampus-bot
docker-compose logs -f postgres
docker-compose logs -f milvus-standalone
```

### 停止服务
```bash
docker-compose down
```

### 重启服务
```bash
docker-compose restart
```

### 重新构建并启动
```bash
docker-compose up -d --build
```

### 清理所有数据（谨慎使用）
```bash
docker-compose down -v
```

### 进入容器
```bash
# 进入后端容器
docker-compose exec echocampus-bot sh

# 进入PostgreSQL容器
docker-compose exec postgres psql -U postgres -d echocampus_bot

# 进入Milvus容器
docker-compose exec milvus-standalone bash
```

## 🗄️ 数据持久化

以下数据卷会持久化存储：

- `postgres_data`: PostgreSQL数据
- `milvus_data`: Milvus向量数据
- `etcd_data`: etcd数据
- `minio_data`: MinIO对象存储数据
- `uploads_data`: 上传文件数据
- `logs_data`: 应用日志数据

数据存储位置：
- Windows: `\\wsl$\docker-desktop-data\data\docker\volumes\`
- Linux: `/var/lib/docker/volumes/`
- Mac: `~/Library/Containers/com.docker.docker/Data/vms/0/data/`

## 🔍 故障排查

### 1. 服务启动失败

查看日志：
```bash
docker-compose logs echocampus-bot
```

常见原因：
- 端口被占用：修改 `.env` 中的端口配置
- 内存不足：增加Docker内存限制
- API密钥错误：检查 `.env` 文件中的API密钥
- 依赖服务未就绪：等待 PostgreSQL 和 Milvus 健康检查通过

### 2. PostgreSQL连接失败

检查PostgreSQL状态：
```bash
docker-compose ps postgres
docker-compose logs postgres
```

手动连接测试：
```bash
docker-compose exec postgres psql -U postgres -d echocampus_bot
```

检查健康状态：
```bash
docker-compose exec postgres pg_isready -U postgres -d echocampus_bot
```

### 3. Milvus连接失败

检查Milvus状态：
```bash
docker-compose ps milvus-standalone
docker-compose logs milvus-standalone
```

检查健康状态：
```bash
docker-compose exec milvus-standalone curl http://localhost:9091/healthz
```

检查依赖服务：
```bash
docker-compose ps etcd minio
```

### 4. 后端服务无法访问

检查后端状态：
```bash
docker-compose ps echocampus-bot
docker-compose logs echocampus-bot
```

检查健康状态：
```bash
docker-compose exec echocampus-bot curl http://localhost:8080/api/v1/health
```

检查依赖服务：
```bash
docker-compose ps postgres milvus-standalone
```

### 5. 502 Bad Gateway（线上部署）

**原因**：使用 `docker run` 独立启动容器，导致数据库连接失败

**解决**：
```bash
cd /home/<用户名>/docker-projects
docker compose down
docker compose up -d
```

### 6. 401 未提供认证token

**原因**：SSE 连接未传递认证 token

**解决**：检查前端代码是否正确传递 JWT token

### 7. 容器启动失败

查看日志：
```bash
docker logs echocampus-bot
```

检查依赖服务：
```bash
docker ps | grep echocampus
```

重启服务：
```bash
cd /home/<用户名>/docker-projects
docker compose restart
```

## 📈 性能优化

### 1. 调整内存限制

编辑Docker Desktop设置，增加内存分配：
- 推荐至少 4GB
- 生产环境建议 8GB 或更多

### 2. 调整数据库连接池

编辑 `application-docker.yml`：
```yaml
spring:
  datasource:
    druid:
      initial-size: 10
      min-idle: 10
      max-active: 50
      max-wait: 60000
```

### 3. 调整Milvus参数

编辑 `application-docker.yml`：
```yaml
milvus:
  nprobe: 20  # 增加搜索精度（默认10）
  nlist: 2048  # 增加索引参数（默认1024）
```

### 4. 调整RAG参数

编辑 `application-docker.yml`：
```yaml
rag:
  top-k: 20  # 增加检索数量（默认15）
  similarity-threshold: 0.3  # 降低相似度阈值（默认0.4）
  max-context-length: 6000  # 增加上下文长度（默认4000）
```

### 5. 调整AI服务参数

编辑 `application-docker.yml`：
```yaml
ai:
  embedding:
    batch-size: 20  # 增加批处理大小（默认10）
  llm:
    max-tokens: 4000  # 增加最大token数（默认2000）
```

## 🔒 安全建议

1. **修改默认密码**
   - 修改 `.env` 中的 `POSTGRES_PASSWORD`
   - 修改 `.env` 中的 `JWT_SECRET`
   - 修改 MinIO 默认密码（生产环境）

2. **限制端口暴露**
   - 生产环境不要暴露数据库端口（5432、19530、9000）
   - 使用反向代理（Nginx）保护后端服务
   - 配置防火墙规则

3. **使用HTTPS**
   - 配置SSL证书
   - 强制使用HTTPS
   - 配置 HSTS

4. **定期备份数据**
   ```bash
   # 备份PostgreSQL
   docker-compose exec postgres pg_dump -U postgres echocampus_bot > backup.sql
   
   # 备份Milvus（需要使用Milvus Backup工具）
   ```

5. **环境变量安全**
   - 不要将 `.env` 文件提交到版本控制
   - 使用密钥管理服务（如 HashiCorp Vault）
   - 定期轮换 API 密钥

6. **容器安全**
   - 使用非 root 用户运行容器（已配置）
   - 定期更新基础镜像
   - 扫描镜像漏洞

## ⚠️ 重要注意事项

### 本地部署

1. **必须先构建后端应用**：在运行 `docker-compose up` 之前，必须先执行 `mvn clean package` 构建 JAR 包
2. **端口冲突**：确保本地端口 5432、8083、8000、9000、9001、19530、9091 未被占用
3. **API密钥**：必须配置有效的阿里云和 DeepSeek API 密钥，否则服务无法正常工作

### 线上部署

1. **必须使用 docker-compose**：服务器上的服务依赖 PostgreSQL、Milvus、etcd、MinIO 等组件，必须通过 docker-compose 启动才能正确连接
2. **不要使用 docker run 独立启动**：独立启动会导致数据库连接失败，出现 502 Bad Gateway 错误
3. **端口映射**：后端服务映射为 `<后端端口>:8080`（宿主机端口:容器端口）
4. **配置文件位置**：服务器上的 docker-compose.yml 位于 `/home/<用户名>/docker-projects/docker-compose.yml`
5. **镜像更新**：每次代码更新后，需要重新构建、推送镜像，然后在服务器上拉取并重启服务

### 服务依赖说明

后端服务依赖以下组件（通过 docker-compose 管理）：

- **PostgreSQL**：主数据库，端口 5432
- **Milvus**：向量数据库，端口 19530
- **etcd**：Milvus 元数据存储
- **MinIO**：Milvus 对象存储，端口 9000

所有服务通过健康检查确保启动顺序正确。


## 📚 更多信息

- [Docker官方文档](https://docs.docker.com/)
- [Docker Compose官方文档](https://docs.docker.com/compose/)
- [Milvus官方文档](https://milvus.io/docs)
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [阿里云百炼平台文档](https://help.aliyun.com/zh/dashscope/)
- [DeepSeek API文档](https://platform.deepseek.com/api-docs/)

## 🆘 获取帮助

如果遇到问题：

1. 查看日志：`docker-compose logs -f`
2. 检查服务状态：`docker-compose ps`
3. 查看配置文件：`application-docker.yml`
4. 查看环境变量：`.env`
5. 检查健康状态：`curl http://localhost:8083/api/v1/health`

## 🔄 更新部署流程

### 本地环境更新

```bash
# 1. 拉取最新代码
git pull

# 2. 重新构建后端
cd backend
mvn clean package -DskipTests
cd ..

# 3. 重启服务
docker-compose down
docker-compose up -d --build

# 4. 验证服务
docker-compose ps
curl http://localhost:8083/api/v1/health
```

### 线上环境更新

```bash
# 本地操作
cd backend
mvn clean package -DskipTests
cd ..
docker build -t echocampus:latest ./backend
docker tag echocampus:latest <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
docker push <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest

# 服务器操作
ssh <用户名>@<服务器IP>
cd /home/<用户名>/docker-projects
docker pull <阿里云镜像仓库地址>/<命名空间>/<仓库名>:latest
docker compose down
docker compose up -d
docker ps | grep echocampus
curl http://localhost:<后端端口>/api/v1/health
```
