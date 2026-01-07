package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置Mapper接口
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 根据配置键查询
     */
    SystemConfig selectByKey(@Param("configKey") String configKey);

    /**
     * 根据前缀查询配置列表
     */
    List<SystemConfig> selectByKeyPrefix(@Param("prefix") String prefix);

    /**
     * 更新配置值
     */
    void updateValueByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
