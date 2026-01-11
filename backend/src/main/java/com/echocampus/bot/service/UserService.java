package com.echocampus.bot.service;

import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;

public interface UserService {

    LoginResponse login(LoginRequest request);

    User register(User user);

    User registerWithVerificationCode(String username, String password, String email, String nickname, String verificationCode);

    User getUserById(Long userId);

    User getUserByUsername(String username);

    void updateUser(User user);

    void changePassword(Long userId, String oldPassword, String newPassword, String verificationCode);
}
