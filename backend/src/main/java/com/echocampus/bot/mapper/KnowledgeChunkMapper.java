package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库片段Mapper接口
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 查询文档的所有片段
     */
    List<KnowledgeChunk> selectByDocId(@Param("docId") Long docId);

    /**
     * 根据向量ID列表查询片段
     */
    List<KnowledgeChunk> selectByVectorIds(@Param("vectorIds") List<String> vectorIds);

    /**
     * 删除文档的所有片段
     */
    void deleteByDocId(@Param("docId") Long docId);

    /**
     * 根据内容哈希查询（用于去重）
     */
    KnowledgeChunk selectByContentHash(@Param("contentHash") String contentHash);
}
