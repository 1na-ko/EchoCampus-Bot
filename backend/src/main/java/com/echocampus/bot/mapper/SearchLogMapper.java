package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检索日志Mapper接口
 */
@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {
}
