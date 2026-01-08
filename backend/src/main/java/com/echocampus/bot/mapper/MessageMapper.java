package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 消息Mapper接口
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 查询会话的所有消息
     */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 查询会话最近的N条消息
     */
    List<Message> selectRecentByConversationId(@Param("conversationId") Long conversationId, @Param("limit") Integer limit);

    /**
     * 统计会话消息数量
     */
    Integer countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 更新消息内容和元数据（处理 JSONB 类型）
     */
    int updateContentAndMetadata(@Param("id") Long id, @Param("content") String content, @Param("metadata") Map<String, Object> metadata);
}
