package com.echocampus.bot.service;

public interface VerificationCodeService {

    void sendVerificationCode(String email, String type, String ipAddress);

    boolean verifyCode(String email, String code, String type);

    void markCodeAsUsed(String email, String code, String type);

    void cleanExpiredCodes();
}
