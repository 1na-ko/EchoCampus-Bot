package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档Mapper接口
 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /**
     * 分页查询文档列表
     */
    IPage<KnowledgeDoc> selectPageByCondition(
            Page<KnowledgeDoc> page,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    /**
     * 查询指定分类的文档数量
     */
    Integer countByCategory(@Param("category") String category);

    /**
     * 更新文档向量数量
     */
    void updateVectorCount(@Param("id") Long id, @Param("vectorCount") Integer vectorCount);

    /**
     * 更新文档处理状态
     */
    void updateProcessStatus(@Param("id") Long id, @Param("processStatus") String processStatus, @Param("processMessage") String processMessage);
}
