package com.echocampus.bot.task;

import com.echocampus.bot.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCodeCleanupTask {

    private final VerificationCodeService verificationCodeService;

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredCodes() {
        log.info("开始清理过期验证码...");
        try {
            verificationCodeService.cleanExpiredCodes();
            log.info("过期验证码清理完成");
        } catch (Exception e) {
            log.error("清理过期验证码失败", e);
        }
    }
}
