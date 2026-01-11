package com.echocampus.bot.service.impl;

import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;
import com.echocampus.bot.mapper.UserMapper;
import com.echocampus.bot.service.UserService;
import com.echocampus.bot.service.VerificationCodeService;
import com.echocampus.bot.utils.DateTimeUtil;
import com.echocampus.bot.utils.JwtUtil;
import com.echocampus.bot.utils.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService verificationCodeService;

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
