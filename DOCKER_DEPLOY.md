# EchoCampus-Bot Docker部署指南

## 📋 前置要求

- Docker Desktop (Windows/Mac) 或 Docker Engine (Linux)
- Docker Compose
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

## 🚀 快速部署

### Windows系统

1. **配置环境变量**
   ```bash
   # 复制示例配置文件
   copy .env.example .env
   
   # 编辑.env文件，填入你的API密钥
   notepad .env
   ```

2. **运行部署脚本**
   ```bash
   deploy.bat
   ```

### Linux/Mac系统

1. **配置环境变量**
   ```bash
   # 复制示例配置文件
   cp .env.example .env
   
   # 编辑.env文件，填入你的API密钥
   nano .env
   ```

2. **运行部署脚本**
   ```bash
   chmod +x deploy.sh
   ./deploy.sh
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

## 🔐 环境变量配置

编辑 `.env` 文件，配置以下变量：

```bash
# PostgreSQL数据库配置
POSTGRES_DB=echocampus_bot
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<REDACTED_WEAK_PASSWORD>
POSTGRES_PORT=5432

# 后端服务端口
BACKEND_PORT=8083

# 阿里云百炼平台API密钥（Embedding服务）
ALIYUN_API_KEY=your_aliyun_api_key_here

# DeepSeek API密钥（LLM服务）
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# JWT密钥（建议修改为随机字符串）
JWT_SECRET=your_jwt_secret_here
```

## 🌐 访问地址

部署成功后，可以通过以下地址访问服务：

- **后端API**: http://localhost:8083/api
- **API文档**: http://localhost:8083/api/doc.html
- **Milvus管理界面**: http://localhost:8000
- **MinIO控制台**: http://localhost:9001 (用户名/密码: <REDACTED_WEAK_PASSWORD>)

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

### 4. 后端服务无法访问

检查后端状态：
```bash
docker-compose ps echocampus-bot
docker-compose logs echocampus-bot
```

检查健康状态：
```bash
docker-compose exec echocampus-bot curl http://localhost:8080/api/health
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
      max-active: 50  # 增加最大连接数
```

### 3. 调整Milvus参数

编辑 `application-docker.yml`：
```yaml
milvus:
  nprobe: 20  # 增加搜索精度
```

## 🔒 安全建议

1. **修改默认密码**
   - 修改 `.env` 中的 `POSTGRES_PASSWORD`
   - 修改 `.env` 中的 `JWT_SECRET`

2. **限制端口暴露**
   - 生产环境不要暴露数据库端口
   - 使用反向代理（Nginx）保护后端服务

3. **使用HTTPS**
   - 配置SSL证书
   - 强制使用HTTPS

4. **定期备份数据**
   ```bash
   # 备份PostgreSQL
   docker-compose exec postgres pg_dump -U postgres echocampus_bot > backup.sql
   
   # 备份Milvus（需要使用Milvus Backup工具）
   ```

## 🚢 部署到服务器

### 1. 上传项目到服务器

```bash
# 使用SCP上传
scp -r EchoCampus-Bot/ student4@150.158.97.39:/home/student4/docker-projects/
```

### 2. SSH连接到服务器

```bash
ssh student4@150.158.97.39
```

### 3. 进入项目目录

```bash
cd /home/student4/docker-projects/EchoCampus-Bot
```

### 4. 配置环境变量

```bash
cp .env.example .env
nano .env
```

### 5. 运行部署脚本

```bash
chmod +x deploy.sh
./deploy.sh
```

### 6. 配置Nginx（可选）

编辑Nginx配置文件，添加反向代理：

```nginx
location /api/ {
    proxy_pass http://localhost:8083/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

### 7. 重启Nginx

```bash
sudo nginx -s reload
```

## 📚 更多信息

- [Docker官方文档](https://docs.docker.com/)
- [Docker Compose官方文档](https://docs.docker.com/compose/)
- [Milvus官方文档](https://milvus.io/docs)
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)

## 🆘 获取帮助

如果遇到问题：

1. 查看日志：`docker-compose logs -f`
2. 检查服务状态：`docker-compose ps`
3. 查看配置文件：`application-docker.yml`
4. 查看环境变量：`.env`
