package com.echocampus.bot.service.impl;

import com.echocampus.bot.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        String subject = "EchoCampus - 邮箱验证码";
        String content = buildVerificationCodeContent(code);
        sendEmail(toEmail, subject, content);
    }

    @Override
    public void sendEmail(String toEmail, String subject, String content) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            javaMailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", toEmail, subject);
        } catch (Exception e) {
            log.error("邮件发送失败: to={}, subject={}", toEmail, subject, e);
            throw new RuntimeException("邮件发送失败: " + e.getMessage());
        }
    }

    private String buildVerificationCodeContent(String code) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset='UTF-8'>\n" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "<title>EchoCampus Check</title>\n" +
                "<style>\n" +
                "  :root {\n" +
                "    --primary-color: #6366f1;\n" +
                "    --primary-gradient: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);\n" +
                "    --surface-color: rgba(255, 255, 255, 0.9);\n" +
                "    --text-primary: #1e293b;\n" +
                "    --text-secondary: #64748b;\n" +
                "    --border-color: rgba(226, 232, 240, 0.8);\n" +
                "  }\n" +
                "  body {\n" +
                "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
                "    background-color: #f8fafc;\n" +
                "    background-image: \n" +
                "      radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.1) 0px, transparent 50%),\n" +
                "      radial-gradient(at 100% 100%, rgba(139, 92, 246, 0.1) 0px, transparent 50%);\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "    -webkit-font-smoothing: antialiased;\n" +
                "  }\n" +
                "  .wrapper {\n" +
                "    width: 100%;\n" +
                "    padding: 40px 20px;\n" +
                "    box-sizing: border-box;\n" +
                "    display: flex;\n" +
                "    justify-content: center;\n" +
                "  }\n" +
                "  .container {\n" +
                "    width: 100%;\n" +
                "    max-width: 520px;\n" +
                "    background: var(--surface-color);\n" +
                "    border-radius: 24px;\n" +
                "    box-shadow: \n" +
                "      0 4px 6px -1px rgba(0, 0, 0, 0.05), \n" +
                "      0 10px 15px -3px rgba(0, 0, 0, 0.05),\n" +
                "      0 0 0 1px rgba(255, 255, 255, 0.5) inset;\n" +
                "    backdrop-filter: blur(20px);\n" +
                "    overflow: hidden;\n" +
                "  }\n" +
                "  .header {\n" +
                "    background: var(--primary-gradient);\n" +
                "    padding: 40px 32px;\n" +
                "    text-align: center;\n" +
                "    position: relative;\n" +
                "  }\n" +
                "  .logo {\n" +
                "    color: white;\n" +
                "    font-size: 28px;\n" +
                "    font-weight: 800;\n" +
                "    letter-spacing: -0.5px;\n" +
                "    margin-bottom: 8px;\n" +
                "    text-shadow: 0 2px 4px rgba(0,0,0,0.1);\n" +
                "  }\n" +
                "  .subtitle {\n" +
                "    color: rgba(255, 255, 255, 0.9);\n" +
                "    font-size: 15px;\n" +
                "    font-weight: 500;\n" +
                "  }\n" +
                "  .content {\n" +
                "    padding: 40px 32px;\n" +
                "  }\n" +
                "  .greeting {\n" +
                "    font-size: 18px;\n" +
                "    color: var(--text-primary);\n" +
                "    font-weight: 600;\n" +
                "    margin-bottom: 16px;\n" +
                "  }\n" +
                "  .message {\n" +
                "    font-size: 15px;\n" +
                "    color: var(--text-secondary);\n" +
                "    line-height: 1.7;\n" +
                "    margin-bottom: 32px;\n" +
                "  }\n" +
                "  .code-box {\n" +
                "    background: rgba(99, 102, 241, 0.04);\n" +
                "    border: 1px solid rgba(99, 102, 241, 0.1);\n" +
                "    border-radius: 16px;\n" +
                "    padding: 32px;\n" +
                "    text-align: center;\n" +
                "    margin-bottom: 32px;\n" +
                "  }\n" +
                "  .code-label {\n" +
                "    font-size: 12px;\n" +
                "    text-transform: uppercase;\n" +
                "    letter-spacing: 1.5px;\n" +
                "    color: var(--text-secondary);\n" +
                "    margin-bottom: 12px;\n" +
                "    font-weight: 600;\n" +
                "  }\n" +
                "  .code {\n" +
                "    font-family: 'Fira Code', 'Menlo', 'Monaco', 'Courier New', monospace;\n" +
                "    font-size: 36px;\n" +
                "    font-weight: 700;\n" +
                "    color: var(--primary-color);\n" +
                "    letter-spacing: 6px;\n" +
                "    margin-bottom: 16px;\n" +
                "  }\n" +
                "  .expiration {\n" +
                "    font-size: 13px;\n" +
                "    color: var(--text-secondary);\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "    justify-content: center;\n" +
                "    gap: 6px;\n" +
                "  }\n" +
                "  .tips {\n" +
                "    background: #fff;\n" +
                "    border-radius: 12px;\n" +
                "    padding: 20px;\n" +
                "    border: 1px solid var(--border-color);\n" +
                "  }\n" +
                "  .tips-title {\n" +
                "    font-size: 14px;\n" +
                "    font-weight: 600;\n" +
                "    color: var(--text-primary);\n" +
                "    margin-bottom: 8px;\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "    gap: 8px;\n" +
                "  }\n" +
                "  .tips-content {\n" +
                "    font-size: 13px;\n" +
                "    color: var(--text-secondary);\n" +
                "    line-height: 1.6;\n" +
                "    margin: 0;\n" +
                "  }\n" +
                "  .footer {\n" +
                "    text-align: center;\n" +
                "    padding: 24px;\n" +
                "    border-top: 1px solid var(--border-color);\n" +
                "    background: rgba(248, 250, 252, 0.5);\n" +
                "  }\n" +
                "  .copyright {\n" +
                "    font-size: 12px;\n" +
                "    color: #94a3b8;\n" +
                "  }\n" +
                "  @media (max-width: 600px) {\n" +
                "    .wrapper { padding: 20px 16px; }\n" +
                "    .header { padding: 32px 24px; }\n" +
                "    .content { padding: 32px 24px; }\n" +
                "    .code { font-size: 32px; }\n" +
                "  }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class='wrapper'>\n" +
                "    <div class='container'>\n" +
                "      <div class='header'>\n" +
                "        <div class='logo'>EchoCampus</div>\n" +
                "        <div class='subtitle'>智能校园问答助手</div>\n" +
                "      </div>\n" +
                "      \n" +
                "      <div class='content'>\n" +
                "        <div class='greeting'>您好！</div>\n" +
                "        <div class='message'>\n" +
                "          您正在进行身份验证，请使用下方的验证码完成操作。验证码仅用于本次验证，请勿泄露给他人。\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class='code-box'>\n" +
                "          <div class='code-label'>VERIFICATION CODE</div>\n" +
                "          <div class='code'>" + code + "</div>\n" +
                "          <div class='expiration'>\n" +
                "            此验证码在 5 分钟内有效\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class='tips'>\n" +
                "          <div class='tips-title'>\n" +
                "            <span>🛡️</span>\n" +
                "            安全提示\n" +
                "          </div>\n" +
                "          <p class='tips-content'>\n" +
                "            如果这不是您的操作，请忽略此邮件。由于您的邮箱可能已被泄露，建议您尽快修改邮箱密码以确保账号安全。\n" +
                "          </p>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "      \n" +
                "      <div class='footer'>\n" +
                "        <div class='copyright'>&copy; 2026 EchoTech Studio from Shanghai Institute of Technology.</div>\n" +
                "        <div class='copyright'>All rights reserved.</div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
