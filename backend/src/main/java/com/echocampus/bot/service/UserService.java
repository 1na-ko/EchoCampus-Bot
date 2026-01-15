package com.echocampus.bot.service;

import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.request.UpdateProfileRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;

public interface UserService {

    LoginResponse login(LoginRequest request);

    User register(User user);

    User registerWithVerificationCode(String username, String password, String email, String nickname, String verificationCode);

    User getUserById(Long userId);

    User getUserByUsername(String username);

    void updateUser(User user);

    /**
     * 更新用户个人资料
     * 如果修改邮箱，需要验证新邮箱的验证码
     *
     * @param userId  用户ID
     * @param request 更新请求
     */
    void updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, String oldPassword, String newPassword, String verificationCode);
}
