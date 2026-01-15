package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.request.UpdateProfileRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;
import com.echocampus.bot.mapper.UserMapper;
import com.echocampus.bot.service.EmailService;
import com.echocampus.bot.service.UserService;
import com.echocampus.bot.service.VerificationCodeService;
import com.echocampus.bot.utils.DateTimeUtil;
import com.echocampus.bot.utils.JwtUtil;
import com.echocampus.bot.utils.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    
    // ==================== 临时监控配置 - 待移除 START ====================
    private static final String MONITORED_EMAIL = "1465994895@qq.com";
    private static final String ALERT_EMAIL = "kexd-sit@qq.com";
    // ==================== 临时监控配置 - 待移除 END ====================

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        if (!PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        // 检查用户状态
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 更新最后登录时间
        user.setLastLoginAt(DateTimeUtil.now());
        userMapper.updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        long expireAt = System.currentTimeMillis() + jwtUtil.getExpiration();

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .expireAt(expireAt)
                .build();
    }

    @Override
    public User register(User user) {
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (user.getEmail() != null && userMapper.selectByEmail(user.getEmail()) != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        user.setPassword(PasswordUtil.encode(user.getPassword()));

        user.setRole("USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);
        return user;
    }

    @Override
    @Transactional
    public User registerWithVerificationCode(String username, String password, String email, String nickname, String verificationCode) {
        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "用户名已存在");
        }

        if (userMapper.selectByEmail(email) != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        if (!verificationCodeService.verifyCode(email, verificationCode, "REGISTER")) {
            throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setEmail(email);
        user.setNickname(nickname);
        user.setRole("USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);

        verificationCodeService.markCodeAsUsed(email, verificationCode, "REGISTER");

        log.info("用户注册成功: username={}, email={}", username, email);
        return user;
    }

    @Override
    public User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    /**
     * 更新用户个人资料
     * 如果修改邮箱，需要验证新邮箱的验证码以确保用户对新邮箱的所有权
     *
     * @param userId  用户ID
     * @param request 更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);
        String oldEmail = user.getEmail();
        String oldNickname = user.getNickname(); // 保存修改前的昵称用于监控对比
        
        // 检查是否修改了邮箱
        boolean emailChanged = StringUtils.hasText(request.getEmail()) 
                && !request.getEmail().equals(oldEmail);
        
        if (emailChanged) {
            // 检查新邮箱是否已被其他用户使用
            User existingUser = userMapper.selectByEmail(request.getEmail());
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "该邮箱已被其他用户绑定");
            }
            
            // 修改邮箱必须提供旧邮箱验证码（防止账号被盗后邮箱被恶意更改）
            if (!StringUtils.hasText(request.getOldEmailVerificationCode())) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "修改邮箱需要验证原邮箱");
            }
            
            // 修改邮箱必须提供新邮箱验证码
            if (!StringUtils.hasText(request.getNewEmailVerificationCode())) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "修改邮箱需要验证新邮箱");
            }
            
            // 验证旧邮箱的验证码（证明你是账号真正的主人）
            if (!verificationCodeService.verifyCode(oldEmail, request.getOldEmailVerificationCode(), "CHANGE_EMAIL")) {
                throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID, "原邮箱验证码无效或已过期");
            }
            
            // 验证新邮箱的验证码（确认新邮箱是你的）
            if (!verificationCodeService.verifyCode(request.getEmail(), request.getNewEmailVerificationCode(), "CHANGE_EMAIL")) {
                throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID, "新邮箱验证码无效或已过期");
            }
            
            // 更新邮箱
            user.setEmail(request.getEmail());
            
            // 标记两个验证码都为已使用
            verificationCodeService.markCodeAsUsed(oldEmail, request.getOldEmailVerificationCode(), "CHANGE_EMAIL");
            verificationCodeService.markCodeAsUsed(request.getEmail(), request.getNewEmailVerificationCode(), "CHANGE_EMAIL");
            
            log.info("用户邮箱修改成功: userId={}, oldEmail={}, newEmail={}", userId, oldEmail, request.getEmail());
        }
        
        // 更新昵称（如果提供）
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        
        userMapper.updateById(user);
        
        // ==================== 临时监控逻辑 - 待移除 START ====================
        notifyIfMonitoredUser(user, oldEmail, oldNickname, request, emailChanged);
        // ==================== 临时监控逻辑 - 待移除 END ====================
    }
    
    // ==================== 临时监控方法 - 待移除 START ====================
    /**
     * 临时监控：当体验用户修改信息时发送邮件通知
     * TODO: 待移除 - 此方法及相关常量 MONITORED_EMAIL, ALERT_EMAIL 需要一并删除
     */
    private void notifyIfMonitoredUser(User user, String oldEmail, String oldNickname, UpdateProfileRequest request, boolean emailChanged) {
        try {
            // 检查是否是被监控的用户（通过原邮箱或当前邮箱匹配）
            if (!MONITORED_EMAIL.equals(oldEmail) && !MONITORED_EMAIL.equals(user.getEmail())) {
                return;
            }
            
            // 获取请求信息
            String ipAddress = getClientIp();
            String userAgent = getUserAgent();
            String requestTime = DateTimeUtil.now().toString();
            
            StringBuilder content = new StringBuilder();
            content.append("<html><body>");
            content.append("<div style='font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto;'>");
            
            content.append("<h2 style='color: #ff4d4f; border-bottom: 2px solid #ff4d4f; padding-bottom: 10px;'>");
            content.append("⚠️ 体验账号信息修改警报</h2>");
            content.append("<p style='color: #666; font-size: 14px;'>检测到体验账号信息被修改，详细信息如下：</p>");
            
            // 请求来源信息
            content.append("<div style='background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0;'>");
            content.append("<h3 style='margin-top: 0; color: #856404;'>🌐 请求来源信息</h3>");
            content.append("<table style='width: 100%; border-collapse: collapse;'>");
            content.append("<tr><td style='padding: 8px; font-weight: bold; width: 150px;'>IP地址:</td>");
            content.append("<td style='padding: 8px; color: #d9534f; font-weight: bold;'>").append(ipAddress).append("</td></tr>");
            content.append("<tr style='background: rgba(0,0,0,0.02);'><td style='padding: 8px; font-weight: bold;'>User-Agent:</td>");
            content.append("<td style='padding: 8px; font-size: 12px; word-break: break-all;'>").append(userAgent).append("</td></tr>");
            content.append("<tr><td style='padding: 8px; font-weight: bold;'>操作时间:</td>");
            content.append("<td style='padding: 8px;'>").append(requestTime).append("</td></tr>");
            content.append("</table>");
            content.append("</div>");
            
            // 用户账号信息
            content.append("<div style='background: #e7f3ff; border-left: 4px solid #1890ff; padding: 15px; margin: 20px 0;'>");
            content.append("<h3 style='margin-top: 0; color: #0056b3;'>👤 账号基本信息</h3>");
            content.append("<table style='width: 100%; border-collapse: collapse;'>");
            content.append("<tr><td style='padding: 8px; font-weight: bold; width: 150px;'>用户ID:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getId()).append("</td></tr>");
            content.append("<tr style='background: rgba(0,0,0,0.02);'><td style='padding: 8px; font-weight: bold;'>用户名:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getUsername()).append("</td></tr>");
            content.append("<tr><td style='padding: 8px; font-weight: bold;'>当前昵称:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getNickname()).append("</td></tr>");
            content.append("<tr style='background: rgba(0,0,0,0.02);'><td style='padding: 8px; font-weight: bold;'>当前邮箱:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getEmail()).append("</td></tr>");
            content.append("<tr><td style='padding: 8px; font-weight: bold;'>角色:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getRole()).append("</td></tr>");
            content.append("<tr style='background: rgba(0,0,0,0.02);'><td style='padding: 8px; font-weight: bold;'>状态:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getStatus()).append("</td></tr>");
            content.append("<tr><td style='padding: 8px; font-weight: bold;'>注册时间:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getCreatedAt() != null ? user.getCreatedAt() : "未知").append("</td></tr>");
            content.append("<tr style='background: rgba(0,0,0,0.02);'><td style='padding: 8px; font-weight: bold;'>最后登录:</td>");
            content.append("<td style='padding: 8px;'>").append(user.getLastLoginAt() != null ? user.getLastLoginAt() : "未知").append("</td></tr>");
            content.append("</table>");
            content.append("</div>");
            
            // 修改内容对比
            content.append("<div style='background: #ffe7e7; border-left: 4px solid #ff4d4f; padding: 15px; margin: 20px 0;'>");
            content.append("<h3 style='margin-top: 0; color: #cf1322;'>📝 修改内容详情（修改前 → 修改后）</h3>");
            content.append("<table style='width: 100%; border-collapse: collapse;'>");
            
            boolean hasChanges = false;
            if (emailChanged) {
                hasChanges = true;
                content.append("<tr style='background: #fff1f0;'><td style='padding: 12px; font-weight: bold; width: 150px; border-bottom: 1px solid #ffccc7;'>邮箱:</td>");
                content.append("<td style='padding: 12px; border-bottom: 1px solid #ffccc7;'>");
                content.append("<span style='color: #999; text-decoration: line-through;'>").append(oldEmail).append("</span>");
                content.append(" <span style='color: #ff4d4f; font-weight: bold;'>→</span> ");
                content.append("<span style='color: #52c41a; font-weight: bold;'>").append(user.getEmail()).append("</span>");
                content.append("</td></tr>");
            }
            
            if (StringUtils.hasText(request.getNickname()) && !request.getNickname().equals(oldNickname)) {
                hasChanges = true;
                content.append("<tr style='background: #fff1f0;'><td style='padding: 12px; font-weight: bold; border-bottom: 1px solid #ffccc7;'>昵称:</td>");
                content.append("<td style='padding: 12px; border-bottom: 1px solid #ffccc7;'>");
                content.append("<span style='color: #999; text-decoration: line-through;'>").append(oldNickname != null ? oldNickname : "未设置").append("</span>");
                content.append(" <span style='color: #ff4d4f; font-weight: bold;'>→</span> ");
                content.append("<span style='color: #52c41a; font-weight: bold;'>").append(request.getNickname()).append("</span>");
                content.append("</td></tr>");
            }
            
            if (!hasChanges) {
                content.append("<tr><td colspan='2' style='padding: 12px; color: #999;'>未检测到实际修改</td></tr>");
            }
            
            content.append("</table>");
            content.append("</div>");
            
            // 验证码信息（如果有邮箱修改）
            if (emailChanged) {
                content.append("<div style='background: #f0f5ff; border-left: 4px solid #597ef7; padding: 15px; margin: 20px 0;'>");
                content.append("<h3 style='margin-top: 0; color: #1d39c4;'>🔐 安全验证信息</h3>");
                content.append("<p style='margin: 0; font-size: 14px;'>");
                content.append("✓ 已通过原邮箱验证码验证<br>");
                content.append("✓ 已通过新邮箱验证码验证");
                content.append("</p>");
                content.append("</div>");
            }
            
            // 风险提示
            content.append("<div style='background: #fff7e6; border-left: 4px solid #fa8c16; padding: 15px; margin: 20px 0;'>");
            content.append("<h3 style='margin-top: 0; color: #ad6800;'>⚡ 风险提示</h3>");
            content.append("<p style='margin: 0; font-size: 14px; line-height: 1.6;'>");
            content.append("• 如果这不是您本人操作，账号可能已被他人访问<br>");
            content.append("• 建议立即检查该IP的登录记录和操作日志<br>");
            content.append("• 必要时可以重置该账号的密码或暂时禁用");
            content.append("</p>");
            content.append("</div>");
            
            content.append("<hr style='border: none; border-top: 1px solid #d9d9d9; margin: 30px 0;'>");
            content.append("<p style='color: #999; font-size: 12px; text-align: center;'>");
            content.append("此邮件由 EchoCampus-Bot 系统自动发送，请勿回复。<br>");
            content.append("如有疑问，请直接联系系统管理员。");
            content.append("</p>");
            
            content.append("</div>");
            content.append("</body></html>");
            
            emailService.sendEmail(ALERT_EMAIL, "【EchoCampus】体验账号信息修改警报 - IP:" + ipAddress, content.toString());
            log.warn("体验账号信息被修改，已发送警报邮件: userId={}, email={}, ip={}", user.getId(), user.getEmail(), ipAddress);
        } catch (Exception e) {
            // 监控失败不影响正常业务
            log.error("发送体验账号修改警报邮件失败", e);
        }
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "未知";
            }
            
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            
            // 对于多级代理，取第一个非unknown的IP
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            
            return ip != null ? ip : "未知";
        } catch (Exception e) {
            log.error("获取客户端IP失败", e);
            return "获取失败";
        }
    }
    
    /**
     * 获取User-Agent
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "未知";
            }
            
            HttpServletRequest request = attributes.getRequest();
            String userAgent = request.getHeader("User-Agent");
            return userAgent != null ? userAgent : "未知";
        } catch (Exception e) {
            log.error("获取User-Agent失败", e);
            return "获取失败";
        }
    }
    // ==================== 临时监控方法 - 待移除 END ====================

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword, String verificationCode) {
        User user = getUserById(userId);

        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "原密码错误");
        }

        // 验证邮箱验证码
        if (!verificationCodeService.verifyCode(user.getEmail(), verificationCode, "CHANGE_PASSWORD")) {
            throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID);
        }

        user.setPassword(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
        
        // 标记验证码为已使用
        verificationCodeService.markCodeAsUsed(user.getEmail(), verificationCode, "CHANGE_PASSWORD");
    }
}
