package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;
import com.echocampus.bot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Tag(name = "User", description = "用户相关接口")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户登录", description = "用户登录并获取Token")
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    @Operation(summary = "用户注册", description = "新用户注册")
    @PostMapping("/auth/register")
    public Result<User> register(@Valid @RequestBody User user) {
        User registeredUser = userService.register(user);
        // 返回时隐藏密码
        registeredUser.setPassword(null);
        return Result.success("注册成功", registeredUser);
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的信息")
    @GetMapping("/user/profile")
    public Result<User> getCurrentUser(
            @Parameter(description = "用户ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        User user = userService.getUserById(userId);
        user.setPassword(null);
        return Result.success(user);
    }

    @Operation(summary = "更新用户信息", description = "更新当前用户的个人信息")
    @PutMapping("/user/profile")
    public Result<Void> updateProfile(
            @Parameter(description = "用户ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @RequestBody User user) {
        user.setId(userId);
        // 不允许通过此接口修改密码和角色
        user.setPassword(null);
        user.setRole(null);
        userService.updateUser(user);
        return Result.success();
    }

    @Operation(summary = "修改密码", description = "修改用户密码")
    @PutMapping("/user/password")
    public Result<Void> changePassword(
            @Parameter(description = "用户ID") @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Parameter(description = "旧密码") @RequestParam String oldPassword,
            @Parameter(description = "新密码") @RequestParam String newPassword) {
        userService.changePassword(userId, oldPassword, newPassword);
        return Result.success();
    }
}
