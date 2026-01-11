package com.echocampus.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocampus.bot.entity.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EmailVerificationCodeMapper extends BaseMapper<EmailVerificationCode> {

    List<EmailVerificationCode> selectUnusedByEmailAndType(@Param("email") String email, @Param("type") String type);

    int countByEmailAndCreatedAfter(@Param("email") String email, @Param("createdAt") LocalDateTime createdAt);

    int deleteExpiredCodes(@Param("expiredAt") LocalDateTime expiredAt);
}
