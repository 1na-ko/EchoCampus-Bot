package com.echocampus.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * pgvector向量数据库配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "vector.pgvector")
public class PgVectorConfig {

    /**
     * 向量维度（需与embedding模型输出维度一致）
     */
    private Integer dimension = 1024;

    /**
     * 向量表名
     */
    private String tableName = "knowledge_vectors";

    /**
     * 索引类型: hnsw, ivfflat
     */
    private String indexType = "hnsw";

    /**
     * 相似度度量类型: cosine, l2, inner_product
     */
    private String distanceType = "cosine";

    /**
     * HNSW索引参数 - 每层最大邻居数
     */
    private Integer hnswM = 16;

    /**
     * HNSW索引参数 - 构建时的动态候选列表大小
     */
    private Integer hnswEfConstruction = 64;

    /**
     * HNSW索引参数 - 搜索时的动态候选列表大小
     */
    private Integer hnswEfSearch = 100;

    /**
     * IVFFlat索引参数 - 聚类数量
     */
    private Integer ivfflatLists = 1000;

    /**
     * IVFFlat索引参数 - 搜索时探测的聚类数
     */
    private Integer ivfflatProbes = 10;

    /**
     * 是否在启动时自动创建表和索引
     */
    private Boolean autoCreateTable = true;

    /**
     * 批量插入大小
     */
    private Integer batchSize = 100;

    /**
     * 获取距离函数的SQL操作符
     */
    public String getDistanceOperator() {
        return switch (distanceType.toLowerCase()) {
            case "l2" -> "<->";           // 欧几里得距离
            case "inner_product" -> "<#>"; // 内积（需要归一化的向量）
            default -> "<=>";              // 余弦距离
        };
    }

    /**
     * 获取索引的操作符类
     */
    public String getIndexOpsClass() {
        return switch (distanceType.toLowerCase()) {
            case "l2" -> "vector_l2_ops";
            case "inner_product" -> "vector_ip_ops";
            default -> "vector_cosine_ops";
        };
    }
}
