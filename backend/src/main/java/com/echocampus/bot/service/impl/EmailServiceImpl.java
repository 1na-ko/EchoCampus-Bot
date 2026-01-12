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
                "<meta charset=\"UTF-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "<title>EchoCampus - 邮箱验证码</title>\n" +
                "<style>\n" +
                "  body {\n" +
                "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
                "    background-color: #f8fafc;\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "    -webkit-text-size-adjust: 100%;\n" +
                "    -ms-text-size-adjust: 100%;\n" +
                "  }\n" +
                "  .wrapper {\n" +
                "    width: 100%;\n" +
                "    table-layout: fixed;\n" +
                "    background-color: #f8fafc;\n" +
                "    padding: 40px 0;\n" +
                "  }\n" +
                "  .container {\n" +
                "    width: 100%;\n" +
                "    max-width: 520px;\n" +
                "    background-color: #ffffff;\n" +
                "    margin: 0 auto;\n" +
                "    border-radius: 16px;\n" +
                "    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 10px 15px -3px rgba(0, 0, 0, 0.05);\n" +
                "    overflow: hidden;\n" +
                "    border: 1px solid #e2e8f0;\n" +
                "  }\n" +
                "  .header {\n" +
                "    background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);\n" +
                "    padding: 38px 40px;\n" +
                "    text-align: center;\n" +
                "  }\n" +
                "  .logo {\n" +
                "    color: #ffffff;\n" +
                "    font-size: 28px;\n" +
                "    font-weight: 800;\n" +
                "    letter-spacing: -0.5px;\n" +
                "    margin: 0;\n" +
                "    text-shadow: 0 2px 4px rgba(0,0,0,0.1);\n" +
                "  }\n" +
                "  .subtitle {\n" +
                "    color: rgba(255, 255, 255, 0.9);\n" +
                "    font-size: 14px;\n" +
                "    margin-top: 8px;\n" +
                "    font-weight: 500;\n" +
                "    letter-spacing: 0.5px;\n" +
                "  }\n" +
                "  .content {\n" +
                "    padding: 40px;\n" +
                "    background-color: #ffffff;\n" +
                "  }\n" +
                "  .code-container {\n" +
                "    background-color: #f8fafc;\n" +
                "    border: 1px solid #e2e8f0;\n" +
                "    border-radius: 12px;\n" +
                "    padding: 24px;\n" +
                "    text-align: center;\n" +
                "    margin: 24px 0;\n" +
                "  }\n" +
                "  .code {\n" +
                "    font-family: 'Courier New', Courier, monospace;\n" +
                "    font-size: 32px;\n" +
                "    font-weight: 700;\n" +
                "    color: #4f46e5;\n" +
                "    letter-spacing: 4px;\n" +
                "    margin: 0;\n" +
                "  }\n" +
                "  .text-secondary {\n" +
                "    color: #64748b;\n" +
                "    font-size: 14px;\n" +
                "    line-height: 1.6;\n" +
                "  }\n" +
                "  .footer {\n" +
                "    background-color: #f8fafc;\n" +
                "    padding: 24px 40px;\n" +
                "    text-align: center;\n" +
                "    border-top: 1px solid #e2e8f0;\n" +
                "  }\n" +
                "  .footer-text {\n" +
                "    color: #94a3b8;\n" +
                "    font-size: 12px;\n" +
                "  }\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <table class=\"wrapper\" role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n" +
                "    <tr>\n" +
                "      <td align=\"center\">\n" +
                "        <table class=\"container\" role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n" +
                "          <!-- Header -->\n" +
                "          <tr>\n" +
                "            <td class=\"header\">\n" +
                "              <h1 class=\"logo\">EchoCampus</h1>\n" +
                "              <div class=\"subtitle\">智能校园问答助手</div>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "          <!-- Content -->\n" +
                "          <tr>\n" +
                "            <td class=\"content\">\n" +
                "              <h2 style=\"color: #1e293b; font-size: 20px; font-weight: 600; margin: 0 0 16px 0;\">验证您的身份</h2>\n" +
                "              <p class=\"text-secondary\" style=\"margin: 0 0 24px 0;\">您好！您正在进行邮箱验证，请使用下方的验证码完成操作。验证码有效期为 5 分钟。</p>\n" +
                "              \n" +
                "              <div class=\"code-container\">\n" +
                "                <div style=\"font-size: 11px; text-transform: uppercase; color: #64748b; letter-spacing: 1px; margin-bottom: 8px; font-weight: 600;\">Verification Code</div>\n" +
                "                <div class=\"code\">" + code + "</div>\n" +
                "              </div>\n" +
                "              \n" +
                "              <p class=\"text-secondary\" style=\"font-size: 13px; margin: 0; background-color: #fef2f2; color: #c026d3; padding: 12px; border-radius: 8px; border: 1px solid #fecaca;\">\n" +
                "                <span style=\"margin-right: 4px;\">⚠️</span>若非本人操作，请忽略此邮件，但您的账号可能存在风险，建议及时修改密码。\n" +
                "              </p>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "          <!-- Footer -->\n" +
                "          <tr>\n" +
                "            <td class=\"footer\">\n" +
                "              <p class=\"footer-text\" style=\"margin: 0 0 8px 0;\">&copy; 2026 EchoTech Studio from Shanghai Institute of Technology.</p>\n" +
                "              <p class=\"footer-text\" style=\"margin: 0;\">All rights reserved.</p>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </table>\n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </table>\n" +
                "</body>\n" +
                "</html>";
    }
}
