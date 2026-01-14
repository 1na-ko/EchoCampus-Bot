package com.echocampus.bot.service.impl;

import com.echocampus.bot.config.VectorProviderConfig;
import com.echocampus.bot.service.MilvusService;
import com.echocampus.bot.service.VectorService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量服务路由器
 * 根据配置自动路由到具体的向量存储实现（pgvector或Milvus）
 * 作为主要的VectorService实现，供业务代码注入使用
 */
@Slf4j
@Service
@Primary
public class VectorServiceRouter implements VectorService {

    private final VectorProviderConfig providerConfig;
    
    @Autowired(required = false)
    @Qualifier("pgVectorService")
    private VectorService pgVectorService;
    
    @Autowired(required = false)
    private MilvusService milvusService;

    private VectorService activeService;

    public VectorServiceRouter(VectorProviderConfig providerConfig) {
        this.providerConfig = providerConfig;
    }

    @PostConstruct
    public void init() {
        selectActiveService();
    }

    /**
     * 根据配置选择活动的向量服务
     */
    private void selectActiveService() {
        String provider = providerConfig.getProvider();
        
        if ("pgvector".equalsIgnoreCase(provider)) {
            if (pgVectorService != null) {
                activeService = pgVectorService;
                log.info("向量服务路由器: 使用 pgvector 作为向量存储后端");
            } else {
                log.warn("pgvector服务不可用，尝试回退到Milvus");
                fallbackToMilvus();
            }
        } else if ("milvus".equalsIgnoreCase(provider)) {
            fallbackToMilvus();
        } else {
            log.warn("未知的向量存储提供者: {}, 默认使用pgvector", provider);
            if (pgVectorService != null) {
                activeService = pgVectorService;
            } else {
                fallbackToMilvus();
            }
        }
        
        if (activeService == null) {
            log.error("没有可用的向量存储服务！向量相关功能将不可用。");
        }
    }

    private void fallbackToMilvus() {
        if (milvusService != null && milvusService.isAvailable()) {
            activeService = new MilvusServiceAdapter(milvusService);
            log.info("向量服务路由器: 使用 Milvus 作为向量存储后端");
        } else {
            log.warn("Milvus服务不可用");
        }
    }

    /**
     * 获取当前活动的向量服务
     */
    public VectorService getActiveService() {
        return activeService;
    }

    /**
     * 切换向量服务提供者
     */
    public void switchProvider(String provider) {
        providerConfig.setProvider(provider);
        selectActiveService();
    }

    @Override
    public void initVectorStore() {
        if (activeService != null) {
            activeService.initVectorStore();
        }
    }

    @Override
    public List<String> insertVectors(List<float[]> vectors, List<Long> chunkIds, List<Long> docIds,
                                       List<String> contents, List<String> categories) {
        if (activeService != null) {
            return activeService.insertVectors(vectors, chunkIds, docIds, contents, categories);
        }
        return List.of();
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK, float threshold) {
        if (activeService != null) {
            return activeService.search(queryVector, topK, threshold);
        }
        return List.of();
    }

    @Override
    public void deleteVectors(List<String> vectorIds) {
        if (activeService != null) {
            activeService.deleteVectors(vectorIds);
        }
    }

    @Override
    public void deleteByDocId(Long docId) {
        if (activeService != null) {
            activeService.deleteByDocId(docId);
        }
    }

    @Override
    public long getVectorCount() {
        if (activeService != null) {
            return activeService.getVectorCount();
        }
        return 0;
    }

    @Override
    public boolean isAvailable() {
        return activeService != null && activeService.isAvailable();
    }

    @Override
    public String getProviderName() {
        if (activeService != null) {
            return activeService.getProviderName();
        }
        return "none";
    }

    /**
     * MilvusService到VectorService的适配器
     */
    private static class MilvusServiceAdapter implements VectorService {
        private final MilvusService milvusService;

        public MilvusServiceAdapter(MilvusService milvusService) {
            this.milvusService = milvusService;
        }

        @Override
        public void initVectorStore() {
            milvusService.initCollection();
        }

        @Override
        public List<String> insertVectors(List<float[]> vectors, List<Long> chunkIds, List<Long> docIds,
                                           List<String> contents, List<String> categories) {
            return milvusService.insertVectors(vectors, chunkIds, docIds, contents, categories);
        }

        @Override
        public List<SearchResult> search(float[] queryVector, int topK, float threshold) {
            List<MilvusService.SearchResult> milvusResults = milvusService.search(queryVector, topK, threshold);
            return milvusResults.stream()
                    .map(mr -> new SearchResult(
                            mr.getVectorId(),
                            mr.getChunkId(),
                            mr.getDocId(),
                            mr.getContent(),
                            mr.getCategory(),
                            mr.getScore()
                    ))
                    .toList();
        }

        @Override
        public void deleteVectors(List<String> vectorIds) {
            milvusService.deleteVectors(vectorIds);
        }

        @Override
        public void deleteByDocId(Long docId) {
            milvusService.deleteByDocId(docId);
        }

        @Override
        public long getVectorCount() {
            return milvusService.getVectorCount();
        }

        @Override
        public boolean isAvailable() {
            return milvusService.isAvailable();
        }

        @Override
        public String getProviderName() {
            return "milvus";
        }
    }
}
