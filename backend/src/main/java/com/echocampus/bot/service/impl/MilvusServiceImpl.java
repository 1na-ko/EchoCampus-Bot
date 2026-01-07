package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.MilvusConfig;
import com.echocampus.bot.service.MilvusService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.*;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Milvus向量数据库服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusServiceImpl implements MilvusService {

    private final MilvusConfig milvusConfig;
    private MilvusServiceClient milvusClient;

    private static final String FIELD_ID = "id";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_CHUNK_ID = "chunk_id";
    private static final String FIELD_DOC_ID = "doc_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_CATEGORY = "category";

    @PostConstruct
    public void init() {
        // 先快速检查端口是否可连接
        if (!isPortOpen(milvusConfig.getHost(), milvusConfig.getPort(), 2000)) {
            log.warn("Milvus服务不可达 ({}:{})\uff0c向量功能将暂时禁用。请启动Milvus后重启应用。", 
                    milvusConfig.getHost(), milvusConfig.getPort());
            return;
        }
        
        try {
            // 连接Milvus（设置较短超时时间避免阻塞启动）
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusConfig.getHost())
                    .withPort(milvusConfig.getPort())
                    .withConnectTimeout(5, TimeUnit.SECONDS) // 5秒超时
                    .build();
            
            milvusClient = new MilvusServiceClient(connectParam);
            log.info("Milvus连接成功: {}:{}", milvusConfig.getHost(), milvusConfig.getPort());
            
            // 初始化集合
            initCollection();
        } catch (Exception e) {
            log.warn("Milvus连接失败，向量功能将暂时禁用: {}", e.getMessage());
            milvusClient = null;
        }
    }
    
    /**
     * 快速检查端口是否可连接
     */
    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        if (milvusClient != null) {
            milvusClient.close();
            log.info("Milvus连接已关闭");
        }
    }

    @Override
    public void initCollection() {
        if (milvusClient == null) {
            log.warn("Milvus客户端未初始化");
            return;
        }

        String collectionName = milvusConfig.getCollectionName();
        
        // 检查集合是否存在
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );

        if (hasCollection.getData()) {
            // 检查维度是否匹配
            R<DescribeCollectionResponse> descResp = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            if (descResp.getStatus() == R.Status.Success.getCode()) {
                // 检查vector字段的维度
                for (FieldSchema field : descResp.getData().getSchema().getFieldsList()) {
                    if (FIELD_VECTOR.equals(field.getName())) {
                        long existingDim = field.getTypeParamsList().stream()
                                .filter(p -> "dim".equals(p.getKey()))
                                .findFirst()
                                .map(p -> Long.parseLong(p.getValue()))
                                .orElse(0L);
                        
                        if (existingDim != milvusConfig.getDimension()) {
                            log.warn("Milvus集合维度不匹配! 现有:{}, 配置:{}, 将删除并重建集合", 
                                    existingDim, milvusConfig.getDimension());
                            dropCollection();
                            break;
                        }
                    }
                }
            }
            
            // 重新检查集合是否存在
            hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            if (hasCollection.getData()) {
                log.info("Milvus集合已存在: {}", collectionName);
                // 加载集合到内存
                loadCollection();
                return;
            }
        }

        // 创建集合
        FieldType idField = FieldType.newBuilder()
                .withName(FIELD_ID)
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName(FIELD_VECTOR)
                .withDataType(DataType.FloatVector)
                .withDimension(milvusConfig.getDimension())
                .build();

        FieldType chunkIdField = FieldType.newBuilder()
                .withName(FIELD_CHUNK_ID)
                .withDataType(DataType.Int64)
                .build();

        FieldType docIdField = FieldType.newBuilder()
                .withName(FIELD_DOC_ID)
                .withDataType(DataType.Int64)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName(FIELD_CONTENT)
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        FieldType categoryField = FieldType.newBuilder()
                .withName(FIELD_CATEGORY)
                .withDataType(DataType.VarChar)
                .withMaxLength(100)
                .build();

        CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("EchoCampus知识库向量集合")
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(chunkIdField)
                .addFieldType(docIdField)
                .addFieldType(contentField)
                .addFieldType(categoryField)
                .build();

        R<RpcStatus> createResult = milvusClient.createCollection(createCollectionParam);
        if (createResult.getStatus() != R.Status.Success.getCode()) {
            log.error("创建Milvus集合失败: {}", createResult.getMessage());
            return;
        }

        log.info("Milvus集合创建成功: {}", collectionName);

        // 创建索引
        createIndex();

        // 加载集合
        loadCollection();
    }

    private void createIndex() {
        String collectionName = milvusConfig.getCollectionName();
        
        CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_VECTOR)
                .withIndexType(IndexType.valueOf(milvusConfig.getIndexType()))
                .withMetricType(MetricType.valueOf(milvusConfig.getMetricType()))
                .withExtraParam("{\"nlist\":" + milvusConfig.getNlist() + "}")
                .build();

        R<RpcStatus> createIndexResult = milvusClient.createIndex(createIndexParam);
        if (createIndexResult.getStatus() == R.Status.Success.getCode()) {
            log.info("Milvus索引创建成功");
        } else {
            log.error("Milvus索引创建失败: {}", createIndexResult.getMessage());
        }
    }

    private void loadCollection() {
        String collectionName = milvusConfig.getCollectionName();
        
        R<RpcStatus> loadResult = milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );

        if (loadResult.getStatus() == R.Status.Success.getCode()) {
            log.info("Milvus集合加载成功: {}", collectionName);
        } else {
            log.warn("Milvus集合加载失败: {}", loadResult.getMessage());
        }
    }

    /**
     * 删除集合
     */
    private void dropCollection() {
        String collectionName = milvusConfig.getCollectionName();
        
        try {
            // 先释放集合
            milvusClient.releaseCollection(
                    ReleaseCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            // 再删除集合
            R<RpcStatus> dropResult = milvusClient.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            if (dropResult.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus集合已删除: {}", collectionName);
            } else {
                log.error("删除Milvus集合失败: {}", dropResult.getMessage());
            }
        } catch (Exception e) {
            log.error("删除Milvus集合异常: {}", e.getMessage());
        }
    }

    @Override
    public List<String> insertVectors(List<float[]> vectors, List<Long> chunkIds, List<Long> docIds,
                                       List<String> contents, List<String> categories) {
        if (milvusClient == null || vectors.isEmpty()) {
            return Collections.emptyList();
        }

        // 生成向量ID
        List<String> vectorIds = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            vectorIds.add(UUID.randomUUID().toString());
        }

        // 转换向量格式
        List<List<Float>> vectorList = new ArrayList<>();
        for (float[] vector : vectors) {
            List<Float> floatList = new ArrayList<>();
            for (float v : vector) {
                floatList.add(v);
            }
            vectorList.add(floatList);
        }

        // 构建插入数据
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FIELD_ID, vectorIds));
        fields.add(new InsertParam.Field(FIELD_VECTOR, vectorList));
        fields.add(new InsertParam.Field(FIELD_CHUNK_ID, chunkIds));
        fields.add(new InsertParam.Field(FIELD_DOC_ID, docIds));
        fields.add(new InsertParam.Field(FIELD_CONTENT, contents));
        fields.add(new InsertParam.Field(FIELD_CATEGORY, categories));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withFields(fields)
                .build();

        R<MutationResult> insertResult = milvusClient.insert(insertParam);
        if (insertResult.getStatus() != R.Status.Success.getCode()) {
            log.error("向量插入失败: {}", insertResult.getMessage());
            return Collections.emptyList();
        }

        log.info("成功插入 {} 条向量", vectors.size());
        return vectorIds;
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK, float threshold) {
        if (milvusClient == null) {
            return Collections.emptyList();
        }

        // 转换查询向量格式
        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) {
            queryVectorList.add(v);
        }

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withMetricType(MetricType.valueOf(milvusConfig.getMetricType()))
                .withOutFields(Arrays.asList(FIELD_ID, FIELD_CHUNK_ID, FIELD_DOC_ID, FIELD_CONTENT, FIELD_CATEGORY))
                .withTopK(topK)
                .withVectors(Collections.singletonList(queryVectorList))
                .withVectorFieldName(FIELD_VECTOR)
                .withParams("{\"nprobe\":" + milvusConfig.getNprobe() + "}")
                .build();

        R<SearchResults> searchResult = milvusClient.search(searchParam);
        if (searchResult.getStatus() != R.Status.Success.getCode()) {
            log.error("向量搜索失败: {}", searchResult.getMessage());
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>();
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResult.getData().getResults());
        
        if (wrapper.getRowRecords(0).isEmpty()) {
            return results;
        }

        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            QueryResultsWrapper.RowRecord row = wrapper.getRowRecords(0).get(i);
            float score = (float) wrapper.getIDScore(0).get(i).getScore();
            
            // COSINE相似度，分数越高越相似，转换为0-1范围
            float similarity = (1 + score) / 2;
            
            if (similarity < threshold) {
                continue;
            }

            SearchResult result = new SearchResult();
            
            // 安全获取字段值，处理类型转换
            Object idObj = row.get(FIELD_ID);
            result.setVectorId(idObj != null ? idObj.toString() : "");
            
            Object chunkIdObj = row.get(FIELD_CHUNK_ID);
            if (chunkIdObj instanceof Long) {
                result.setChunkId((Long) chunkIdObj);
            } else if (chunkIdObj != null) {
                result.setChunkId(Long.parseLong(chunkIdObj.toString()));
            }
            
            Object docIdObj = row.get(FIELD_DOC_ID);
            if (docIdObj instanceof Long) {
                result.setDocId((Long) docIdObj);
            } else if (docIdObj != null) {
                result.setDocId(Long.parseLong(docIdObj.toString()));
            }
            
            Object contentObj = row.get(FIELD_CONTENT);
            result.setContent(contentObj != null ? contentObj.toString() : "");
            
            Object categoryObj = row.get(FIELD_CATEGORY);
            result.setCategory(categoryObj != null ? categoryObj.toString() : "");
            
            result.setScore(similarity);
            
            results.add(result);
        }

        log.info("向量搜索完成，返回 {} 条结果", results.size());
        return results;
    }

    @Override
    public void deleteVectors(List<String> vectorIds) {
        if (milvusClient == null || vectorIds.isEmpty()) {
            return;
        }

        String expr = FIELD_ID + " in [\"" + String.join("\",\"", vectorIds) + "\"]";
        
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withExpr(expr)
                .build();

        R<MutationResult> deleteResult = milvusClient.delete(deleteParam);
        if (deleteResult.getStatus() == R.Status.Success.getCode()) {
            log.info("成功删除 {} 条向量", vectorIds.size());
        } else {
            log.error("向量删除失败: {}", deleteResult.getMessage());
        }
    }

    @Override
    public void deleteByDocId(Long docId) {
        if (milvusClient == null) {
            return;
        }

        String expr = FIELD_DOC_ID + " == " + docId;
        
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withExpr(expr)
                .build();

        R<MutationResult> deleteResult = milvusClient.delete(deleteParam);
        if (deleteResult.getStatus() == R.Status.Success.getCode()) {
            log.info("成功删除文档 {} 的所有向量", docId);
        } else {
            log.error("删除文档向量失败: {}", deleteResult.getMessage());
        }
    }

    @Override
    public long getVectorCount() {
        if (milvusClient == null) {
            return 0;
        }

        R<GetCollectionStatisticsResponse> statsResult = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(milvusConfig.getCollectionName())
                        .build()
        );

        if (statsResult.getStatus() == R.Status.Success.getCode()) {
            for (KeyValuePair kv : statsResult.getData().getStatsList()) {
                if ("row_count".equals(kv.getKey())) {
                    return Long.parseLong(kv.getValue());
                }
            }
        }
        return 0;
    }

    @Override
    public boolean isAvailable() {
        if (milvusClient == null) {
            return false;
        }
        
        try {
            // 使用较短超时快速检查
            R<Boolean> result = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(milvusConfig.getCollectionName())
                            .withDatabaseName("default")
                            .build()
            );
            return result.getStatus() == R.Status.Success.getCode();
        } catch (Exception e) {
            log.debug("Milvus可用性检查失败: {}", e.getMessage());
            return false;
        }
    }
}
