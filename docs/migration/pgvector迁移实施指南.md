# PostgreSQL + pgvector 迁移实施指南

> 版本: 1.0.0  
> 更新日期: 2026年1月14日  
> 适用范围: EchoCampus-Bot 向量存储架构迁移

## 📋 目录

1. [迁移概述](#1-迁移概述)
2. [准备工作](#2-准备工作)
3. [方案一：全新部署](#3-方案一全新部署)
4. [方案二：平滑迁移](#4-方案二平滑迁移)
5. [验证与测试](#5-验证与测试)
6. [回滚预案](#6-回滚预案)
7. [性能优化](#7-性能优化)
8. [常见问题](#8-常见问题)

---

## 1. 迁移概述

### 1.1 架构变更

| 项目 | 迁移前 | 迁移后 |
|------|--------|--------|
| 向量存储 | Milvus | PostgreSQL + pgvector |
| 依赖服务 | PostgreSQL + Milvus + etcd + MinIO | PostgreSQL (含pgvector扩展) |
| 部署复杂度 | 高（4个服务） | 低（1个服务） |
| 资源消耗 | 高 | 低 |
| 维护成本 | 高 | 低 |

### 1.2 迁移收益

- **简化架构**: 从4个服务减少到1个服务
- **降低成本**: 减少约50%的服务器资源消耗
- **统一运维**: 只需维护PostgreSQL一个数据库
- **数据一致性**: 业务数据和向量数据在同一数据库中
- **事务支持**: 支持跨表事务操作

### 1.3 迁移风险

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 数据丢失 | 中 | 完整备份 + 增量验证 |
| 性能下降 | 低 | 索引优化 + 压测验证 |
| 服务中断 | 中 | 支持在线迁移 + 回滚预案 |
| 兼容性问题 | 低 | 抽象接口 + 充分测试 |

---

## 2. 准备工作

### 2.1 环境要求

#### 2.1.1 软件版本

```yaml
PostgreSQL: 15+ (推荐16)
pgvector: 0.5.0+
Docker: 20.10+
Docker Compose: 2.0+
JDK: 17+
Maven: 3.8+
```

#### 2.1.2 硬件建议

| 配置项 | 最低要求 | 推荐配置 |
|--------|----------|----------|
| CPU | 2核 | 4核+ |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 50GB SSD | 100GB+ NVMe SSD |
| 网络 | 100Mbps | 1Gbps |

### 2.2 数据备份

#### 2.2.1 PostgreSQL备份

```bash
# 完整备份
docker exec echocampus-postgres pg_dump -U echocampus -d echocampus > backup_$(date +%Y%m%d_%H%M%S).sql

# 仅备份knowledge相关表
docker exec echocampus-postgres pg_dump -U echocampus -d echocampus \
    -t knowledge_docs -t knowledge_chunks -t knowledge_categories \
    > knowledge_backup_$(date +%Y%m%d_%H%M%S).sql
```

#### 2.2.2 Milvus备份（如有数据）

```bash
# 导出Milvus集合信息
# 注意：Milvus的向量数据将通过重建方式迁移
docker logs echocampus-milvus 2>&1 | grep "collection" > milvus_collections.log
```

### 2.3 代码更新

确保已拉取最新代码：

```bash
git pull origin main
cd backend
mvn clean compile
```

---

## 3. 方案一：全新部署

适用于新服务器或无历史数据的场景。

### 3.1 部署步骤

#### 步骤1：准备环境变量

创建 `.env` 文件：

```bash
# 数据库配置
POSTGRES_DB=echocampus
POSTGRES_USER=echocampus
POSTGRES_PASSWORD=your_secure_password_here
POSTGRES_PORT=5432

# 向量存储配置
VECTOR_PROVIDER=pgvector

# AI服务配置
ALIYUN_API_KEY=your_aliyun_api_key
DEEPSEEK_API_KEY=your_deepseek_api_key

# JWT配置
JWT_SECRET=your_jwt_secret_key

# 邮件配置（可选）
MAIL_USERNAME=your_email
MAIL_PASSWORD=your_email_password

# CORS配置
CORS_ALLOWED_ORIGINS=http://localhost:3100,https://your-domain.com

# 端口配置
BACKEND_PORT=8083
FRONTEND_PORT=3100
```

#### 步骤2：启动服务

```bash
# 使用pgvector版本的docker-compose
docker-compose -f docker-compose.pgvector.yml up -d

# 检查服务状态
docker-compose -f docker-compose.pgvector.yml ps

# 查看日志
docker-compose -f docker-compose.pgvector.yml logs -f echocampus-bot
```

#### 步骤3：验证部署

```bash
# 检查API健康状态
curl http://localhost:8083/api/v1/health

# 检查pgvector扩展
docker exec echocampus-postgres psql -U echocampus -d echocampus -c "SELECT extversion FROM pg_extension WHERE extname = 'vector';"

# 检查向量表
docker exec echocampus-postgres psql -U echocampus -d echocampus -c "SELECT COUNT(*) FROM knowledge_vectors;"
```

#### 步骤4：测试功能

1. 访问前端页面，上传测试文档
2. 等待文档处理完成
3. 进行问答测试，验证向量搜索功能

---

## 4. 方案二：平滑迁移

适用于已有Milvus数据需要迁移的场景。

### 4.1 迁移流程

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  1. 准备环境     │ => │  2. 启动pgvector │ => │  3. 数据迁移     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                      │
                                                      ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  6. 清理Milvus   │ <= │  5. 切换流量     │ <= │  4. 验证数据     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 4.2 详细步骤

#### 步骤1：更新配置文件

修改 `application.yml`，设置向量提供者为pgvector：

```yaml
vector:
  provider: pgvector  # 更改为pgvector
  enabled: true
  dimension: 1024
```

#### 步骤2：启动pgvector服务

```bash
# 如果使用现有PostgreSQL，需要安装pgvector扩展
docker exec echocampus-postgres psql -U echocampus -d echocampus -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 或启动新的pgvector容器
docker-compose -f docker-compose.pgvector.yml up -d postgres
```

#### 步骤3：重启后端服务

```bash
# 重启后端，自动创建向量表
docker-compose restart echocampus-bot

# 或使用新配置文件
docker-compose -f docker-compose.pgvector.yml up -d echocampus-bot
```

#### 步骤4：执行数据迁移

**方法A：使用迁移脚本（推荐）**

```powershell
# Windows PowerShell
.\scripts\migrate-to-pgvector.ps1 -ApiBaseUrl "http://localhost:8083/api" -BatchSize 50 -Reindex $true
```

```bash
# Linux/Mac
chmod +x scripts/migrate-to-pgvector.sh
./scripts/migrate-to-pgvector.sh
```

**方法B：使用API手动迁移**

```bash
# 启动迁移任务
curl -X POST "http://localhost:8083/api/v1/admin/migration/milvus-to-pgvector?batchSize=50&reindex=true"

# 查询迁移进度
curl "http://localhost:8083/api/v1/admin/migration/progress"

# 验证迁移结果
curl -X POST "http://localhost:8083/api/v1/admin/migration/validate"
```

#### 步骤5：验证数据完整性

```bash
# 对比向量数量
echo "知识片段数量:"
docker exec echocampus-postgres psql -U echocampus -d echocampus -c "SELECT COUNT(*) FROM knowledge_chunks WHERE vector_id IS NOT NULL;"

echo "向量数量:"
docker exec echocampus-postgres psql -U echocampus -d echocampus -c "SELECT COUNT(*) FROM knowledge_vectors;"
```

#### 步骤6：功能验证

1. 进行多次问答测试
2. 对比迁移前后的搜索结果相似度
3. 检查响应时间

#### 步骤7：停止Milvus服务（可选）

确认迁移成功后：

```bash
# 停止Milvus及相关服务
docker stop echocampus-milvus echocampus-etcd echocampus-minio

# 可选：删除容器和数据卷
docker rm echocampus-milvus echocampus-etcd echocampus-minio
docker volume rm echocampus-bot_milvus_data echocampus-bot_etcd_data echocampus-bot_minio_data
```

---

## 5. 验证与测试

### 5.1 功能测试

#### 5.1.1 向量插入测试

```bash
# 上传测试文档
curl -X POST "http://localhost:8083/api/v1/knowledge/docs/upload" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@test_document.pdf" \
  -F "title=测试文档" \
  -F "category=测试分类"
```

#### 5.1.2 向量搜索测试

```bash
# 进行问答测试
curl -X POST "http://localhost:8083/api/v1/chat/message" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": 1,
    "content": "测试问题内容"
  }'
```

### 5.2 性能测试

#### 5.2.1 搜索延迟测试

```sql
-- 在PostgreSQL中执行
EXPLAIN ANALYZE
SELECT id, chunk_id, doc_id, content, category,
       (1 - (vector <=> '[0.1,0.2,...]'::vector)) AS similarity
FROM knowledge_vectors
ORDER BY vector <=> '[0.1,0.2,...]'::vector
LIMIT 10;
```

#### 5.2.2 并发测试

```bash
# 使用ab工具进行压测
ab -n 100 -c 10 -H "Authorization: Bearer YOUR_TOKEN" \
  "http://localhost:8083/api/v1/health"
```

### 5.3 数据一致性验证

```sql
-- 检查向量与chunks的关联
SELECT 
    (SELECT COUNT(*) FROM knowledge_chunks WHERE vector_id IS NOT NULL) AS chunks_with_vector,
    (SELECT COUNT(*) FROM knowledge_vectors) AS total_vectors,
    CASE 
        WHEN (SELECT COUNT(*) FROM knowledge_chunks WHERE vector_id IS NOT NULL) = 
             (SELECT COUNT(*) FROM knowledge_vectors) 
        THEN '✓ 数据一致'
        ELSE '✗ 数据不一致'
    END AS status;
```

---

## 6. 回滚预案

### 6.1 快速回滚

如果迁移出现问题，可快速回滚到Milvus：

#### 步骤1：修改配置

```yaml
# application.yml
vector:
  provider: milvus  # 改回milvus
```

#### 步骤2：重启服务

```bash
# 使用原来的docker-compose
docker-compose -f docker-compose.prod.yml up -d
```

#### 步骤3：验证回滚

```bash
curl http://localhost:8083/api/v1/health
```

### 6.2 数据回滚

如果需要将数据从pgvector迁移回Milvus：

```bash
# 使用迁移API
curl -X POST "http://localhost:8083/api/v1/admin/migration/pgvector-to-milvus?batchSize=50"
```

---

## 7. 性能优化

### 7.1 PostgreSQL配置优化

在 `docker-compose.pgvector.yml` 中已包含优化配置：

```yaml
command: >
  postgres
  -c shared_buffers=256MB          # 共享缓冲区
  -c effective_cache_size=1GB      # 预估可用缓存
  -c maintenance_work_mem=256MB    # 维护操作内存
  -c work_mem=64MB                 # 查询操作内存
  -c max_parallel_workers_per_gather=2
  -c max_parallel_workers=4
  -c random_page_cost=1.1          # SSD优化
  -c effective_io_concurrency=200  # 并发IO
```

### 7.2 pgvector索引优化

#### HNSW索引参数调优

```yaml
# application.yml
vector:
  pgvector:
    hnsw-m: 16              # 每层连接数，增大提高召回率但增加内存
    hnsw-ef-construction: 64 # 构建时搜索范围，增大提高索引质量
    hnsw-ef-search: 100      # 搜索时搜索范围，增大提高召回率但降低速度
```

#### 索引重建（数据量变化大时）

```sql
-- 重建HNSW索引
DROP INDEX IF EXISTS idx_knowledge_vectors_vector_hnsw;
CREATE INDEX idx_knowledge_vectors_vector_hnsw 
ON knowledge_vectors USING hnsw (vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

### 7.3 查询优化

```sql
-- 设置搜索参数
SET hnsw.ef_search = 100;

-- 预热索引
SELECT COUNT(*) FROM knowledge_vectors WHERE vector <=> '[...]'::vector < 0.5;
```

---

## 8. 常见问题

### Q1: pgvector扩展安装失败

**问题**: `CREATE EXTENSION vector` 报错

**解决方案**:
```bash
# 使用官方pgvector镜像
docker pull pgvector/pgvector:pg16

# 或在现有PostgreSQL中安装
apt-get update && apt-get install -y postgresql-16-pgvector
```

### Q2: 向量维度不匹配

**问题**: 插入向量时报维度错误

**解决方案**:
1. 检查embedding模型输出维度
2. 确保配置文件中的dimension与模型一致
3. 如需修改维度，需要重建向量表

### Q3: 搜索结果为空

**问题**: 向量搜索返回空结果

**排查步骤**:
```sql
-- 检查向量表是否有数据
SELECT COUNT(*) FROM knowledge_vectors;

-- 检查索引是否存在
SELECT indexname FROM pg_indexes WHERE tablename = 'knowledge_vectors';

-- 测试搜索（降低阈值）
SELECT * FROM search_similar_vectors('[0.1,...]'::vector, 10, 0.0);
```

### Q4: 迁移任务卡住

**问题**: 迁移进度长时间不变

**解决方案**:
```bash
# 检查后端日志
docker logs echocampus-bot --tail 100

# 取消当前迁移
curl -X POST "http://localhost:8083/api/v1/admin/migration/cancel"

# 重新开始迁移
curl -X POST "http://localhost:8083/api/v1/admin/migration/milvus-to-pgvector?batchSize=20&reindex=true"
```

### Q5: 性能下降

**问题**: 迁移后搜索速度变慢

**优化建议**:
1. 增加 `hnsw.ef_search` 参数
2. 确保SSD磁盘和足够内存
3. 执行 `VACUUM ANALYZE knowledge_vectors`
4. 考虑增加 PostgreSQL 连接池大小

---

## 📞 技术支持

如遇到其他问题，请：

1. 查看 [技术迁移可行性报告](技术迁移可行性报告_PostgreSQL+pgvector.md)
2. 检查后端日志：`docker logs echocampus-bot`
3. 提交Issue到项目仓库

---

*文档结束*
