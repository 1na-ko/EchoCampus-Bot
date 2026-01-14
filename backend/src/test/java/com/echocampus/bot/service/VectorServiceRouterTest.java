package com.echocampus.bot.service;

import com.echocampus.bot.config.VectorProviderConfig;
import com.echocampus.bot.service.impl.VectorServiceRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorServiceRouter单元测试
 * 测试路由器的配置和基本行为
 */
@DisplayName("VectorServiceRouter单元测试")
class VectorServiceRouterTest {

    private VectorProviderConfig providerConfig;
    private VectorServiceRouter router;

    @BeforeEach
    void setUp() {
        providerConfig = new VectorProviderConfig();
        router = new VectorServiceRouter(providerConfig);
    }

    @Test
    @DisplayName("测试默认配置-无可用服务")
    void testDefaultConfigNoServices() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        // 没有注入服务时应该显示不可用
        assertFalse(router.isAvailable());
        assertEquals("none", router.getProviderName());
    }

    @Test
    @DisplayName("测试搜索方法-无可用服务返回空列表")
    void testSearchNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        float[] queryVector = new float[1024];
        List<VectorService.SearchResult> results = router.search(queryVector, 5, 0.5f);
        
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("测试插入向量-无可用服务返回空列表")
    void testInsertVectorsNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        float[] vector = new float[1024];
        List<String> result = router.insertVectors(
                List.of(vector), List.of(1L), List.of(1L), 
                List.of("content"), List.of("category"));
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试删除向量-无可用服务不抛异常")
    void testDeleteVectorsNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        // 应该不抛出异常
        assertDoesNotThrow(() -> router.deleteVectors(List.of("uuid-1", "uuid-2")));
    }

    @Test
    @DisplayName("测试按文档ID删除-无可用服务不抛异常")
    void testDeleteByDocIdNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        assertDoesNotThrow(() -> router.deleteByDocId(123L));
    }

    @Test
    @DisplayName("测试获取向量数量-无可用服务返回0")
    void testGetVectorCountNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        assertEquals(0, router.getVectorCount());
    }

    @Test
    @DisplayName("测试初始化向量存储-无可用服务不抛异常")
    void testInitVectorStoreNoService() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        assertDoesNotThrow(() -> router.initVectorStore());
    }

    @Test
    @DisplayName("测试切换到不存在的提供者")
    void testSwitchToNonExistentProvider() {
        providerConfig.setProvider("pgvector");
        router.init();
        
        // 切换到不存在的提供者应该保持不可用状态
        router.switchProvider("nonexistent");
        assertFalse(router.isAvailable());
    }

    @Test
    @DisplayName("测试VectorProviderConfig默认值")
    void testVectorProviderConfigDefaults() {
        VectorProviderConfig config = new VectorProviderConfig();
        
        // 验证默认值
        assertEquals("pgvector", config.getProvider());
        assertTrue(config.getEnabled());
    }

    @Test
    @DisplayName("测试VectorProviderConfig设置")
    void testVectorProviderConfigSettings() {
        VectorProviderConfig config = new VectorProviderConfig();
        config.setProvider("milvus");
        config.setEnabled(false);
        
        assertEquals("milvus", config.getProvider());
        assertFalse(config.getEnabled());
    }
}
