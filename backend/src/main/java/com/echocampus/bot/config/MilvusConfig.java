package com.echocampus.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus向量数据库配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {

    /**
     * Milvus服务器地址
     */
    private String host = "localhost";

    /**
     * Milvus服务器端口
     */
    private Integer port = 19530;

    /**
     * 集合名称
     */
    private String collectionName = "echocampus_knowledge";

    /**
     * 向量维度
     */
    private Integer dimension = 1536;

    /**
     * 相似度度量类型: L2, IP, COSINE
     */
    private String metricType = "COSINE";

    /**
     * 索引类型: IVF_FLAT, HNSW等
     */
    private String indexType = "IVF_FLAT";

    /**
     * IVF索引的聚类数
     */
    private Integer nlist = 1024;

    /**
     * 搜索时探测的聚类数
     */
    private Integer nprobe = 10;
}
