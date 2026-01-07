package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocampus.bot.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话会话Mapper接口
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 分页查询用户的会话列表
     */
    IPage<Conversation> selectPageByUserId(Page<Conversation> page, @Param("userId") Long userId, @Param("status") String status);

    /**
     * 查询用户最近的会话
     */
    List<Conversation> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);
}
