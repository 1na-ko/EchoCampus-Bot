package com.echocampus.bot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.entity.EmailVerificationCode;
import com.echocampus.bot.mapper.EmailVerificationCodeMapper;
import com.echocampus.bot.service.EmailService;
import com.echocampus.bot.service.VerificationCodeService;
import com.echocampus.bot.utils.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private final EmailVerificationCodeMapper verificationCodeMapper;
    private final EmailService emailService;

    @Value("${verification-code.expire-minutes:5}")
    private int expireMinutes;

    @Value("${verification-code.code-length:6}")
    private int codeLength;

    @Value("${verification-code.send-interval-seconds:60}")
    private int sendIntervalSeconds;

    @Value("${verification-code.max-send-per-day:5}")
    private int maxSendPerDay;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public void sendVerificationCode(String email, String type, String ipAddress) {
        LocalDateTime now = DateTimeUtil.now();

        List<EmailVerificationCode> unusedCodes = verificationCodeMapper.selectUnusedByEmailAndType(email, type);
        if (!unusedCodes.isEmpty()) {
            EmailVerificationCode latestCode = unusedCodes.get(0);
            LocalDateTime createdAt = latestCode.getCreatedAt();
            LocalDateTime nextAllowedTime = createdAt.plusSeconds(sendIntervalSeconds);
            if (nextAllowedTime.isAfter(now)) {
                long remainingSeconds = Duration.between(now, nextAllowedTime).getSeconds();
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, 
                    String.format("请等待 %d 秒后再试", remainingSeconds));
            }
        }

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        int todayCount = verificationCodeMapper.countByEmailAndCreatedAfter(email, startOfDay);
        if (todayCount >= maxSendPerDay) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, 
                String.format("今日发送次数已达上限（%d次），请明天再试", maxSendPerDay));
        }

        String code = generateCode();
        LocalDateTime expiredAt = now.plusMinutes(expireMinutes);

        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setType(type);
        verificationCode.setExpiredAt(expiredAt);
        verificationCode.setUsed(false);
        verificationCode.setIpAddress(ipAddress);

        verificationCodeMapper.insert(verificationCode);

        emailService.sendVerificationCode(email, code);

        log.info("验证码发送成功: email={}, type={}, ip={}", email, type, ipAddress);
    }

    @Override
    @Transactional
    public boolean verifyCode(String email, String code, String type) {
        LocalDateTime now = DateTimeUtil.now();

        LambdaQueryWrapper<EmailVerificationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getCode, code)
                .eq(EmailVerificationCode::getType, type)
                .eq(EmailVerificationCode::getUsed, false)
                .gt(EmailVerificationCode::getExpiredAt, now)
                .orderByDesc(EmailVerificationCode::getCreatedAt)
                .last("LIMIT 1");

        EmailVerificationCode verificationCode = verificationCodeMapper.selectOne(wrapper);

        if (verificationCode == null) {
            log.warn("验证码验证失败: email={}, code={}, type={}", email, code, type);
            return false;
        }

        log.info("验证码验证成功: email={}, type={}", email, type);
        return true;
    }

    @Override
    @Transactional
    public void markCodeAsUsed(String email, String code, String type) {
        LocalDateTime now = DateTimeUtil.now();

        LambdaQueryWrapper<EmailVerificationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getCode, code)
                .eq(EmailVerificationCode::getType, type)
                .eq(EmailVerificationCode::getUsed, false)
                .gt(EmailVerificationCode::getExpiredAt, now)
                .orderByDesc(EmailVerificationCode::getCreatedAt)
                .last("LIMIT 1");

        EmailVerificationCode verificationCode = verificationCodeMapper.selectOne(wrapper);

        if (verificationCode != null) {
            verificationCode.setUsed(true);
            verificationCode.setUsedAt(now);
            verificationCodeMapper.updateById(verificationCode);
            log.info("验证码已标记为已使用: email={}, type={}", email, type);
        }
    }

    @Override
    @Transactional
    public void cleanExpiredCodes() {
        LocalDateTime now = DateTimeUtil.now();
        int deletedCount = verificationCodeMapper.deleteExpiredCodes(now);
        if (deletedCount > 0) {
            log.info("清理过期验证码: 删除 {} 条记录", deletedCount);
        }
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
