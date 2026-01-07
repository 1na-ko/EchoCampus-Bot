package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.KnowledgeCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库分类Mapper接口
 */
@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {

    /**
     * 查询所有顶级分类
     */
    List<KnowledgeCategory> selectTopLevel();

    /**
     * 查询子分类
     */
    List<KnowledgeCategory> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 根据名称查询分类
     */
    KnowledgeCategory selectByName(@Param("name") String name);
}
