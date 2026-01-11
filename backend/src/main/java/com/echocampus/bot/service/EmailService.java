package com.echocampus.bot.service;

public interface EmailService {

    void sendVerificationCode(String toEmail, String code);

    void sendEmail(String toEmail, String subject, String content);
}
