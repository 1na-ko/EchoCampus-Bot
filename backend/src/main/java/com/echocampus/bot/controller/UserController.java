package com.echocampus.bot.controller;

import com.echocampus.bot.annotation.OpLog;
import com.echocampus.bot.common.Result;
import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.request.RegisterWithCodeRequest;
import com.echocampus.bot.dto.request.SendVerificationCodeRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.OperationLog;
import com.echocampus.bot.entity.User;
import com.echocampus.bot.service.UserService;
import com.echocampus.bot.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "用户相关接口")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;

    @Operation(summary = "用户登录", description = "用户登录并获取Token")
    @PostMapping("/auth/login")
    @OpLog(
            operationType = OperationLog.OperationType.LOGIN,
            resourceType = OperationLog.ResourceType.USER,
            description = "用户登录",
            saveRequestParams = false
    )
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    @Operation(summary = "用户注册", description = "新用户注册")
    @PostMapping("/auth/register")
    @OpLog(
            operationType = OperationLog.OperationType.REGISTER,
            resourceType = OperationLog.ResourceType.USER,
            description = "用户注册"
    )
    public Result<User> register(@Valid @RequestBody User user) {
        User registeredUser = userService.register(user);
        registeredUser.setPassword(null);
        return Result.success("注册成功", registeredUser);
    }

    @Operation(summary = "发送验证码", description = "发送邮箱验证码")
    @PostMapping("/auth/send-verification-code")
    @OpLog(
            operationType = OperationLog.OperationType.SEND_CODE,
            resourceType = OperationLog.ResourceType.VERIFICATION_CODE,
            description = "发送邮箱验证码"
    )
    public Result<Void> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        verificationCodeService.sendVerificationCode(request.getEmail(), request.getType(), ipAddress);
        return Result.success();
    }

    @Operation(summary = "用户注册（带验证码）", description = "使用邮箱验证码注册新用户")
    @PostMapping("/auth/register-with-code")
    @OpLog(
            operationType = OperationLog.OperationType.REGISTER,
            resourceType = OperationLog.ResourceType.USER,
            description = "用户注册（带验证码）"
    )
    public Result<User> registerWithCode(@Valid @RequestBody RegisterWithCodeRequest request) {
        User registeredUser = userService.registerWithVerificationCode(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getNickname(),
                request.getVerificationCode()
        );
        registeredUser.setPassword(null);
        return Result.success("注册成功", registeredUser);
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的信息")
    @GetMapping("/user/profile")
    @OpLog(
            operationType = OperationLog.OperationType.QUERY,
            resourceType = OperationLog.ResourceType.USER,
            description = "获取当前用户信息"
    )
    public Result<User> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getUserById(userId);
        user.setPassword(null);
        return Result.success(user);
    }

    @Operation(summary = "更新用户信息", description = "更新当前用户的个人信息")
    @PutMapping("/user/profile")
    @OpLog(
            operationType = OperationLog.OperationType.UPDATE,
            resourceType = OperationLog.ResourceType.USER,
            description = "更新用户信息"
    )
    public Result<Void> updateProfile(HttpServletRequest request, @RequestBody User user) {
        Long userId = (Long) request.getAttribute("userId");
        user.setId(userId);
        // 不允许通过此接口修改密码和角色
        user.setPassword(null);
        user.setRole(null);
        userService.updateUser(user);
        return Result.success();
    }

    @Operation(summary = "修改密码", description = "修改用户密码")
    @PutMapping("/user/password")
    @OpLog(
            operationType = OperationLog.OperationType.CHANGE_PASSWORD,
            resourceType = OperationLog.ResourceType.USER,
            description = "修改密码",
            saveRequestParams = false
    )
    public Result<Void> changePassword(HttpServletRequest request,
            @Parameter(description = "旧密码") @RequestParam String oldPassword,
            @Parameter(description = "新密码") @RequestParam String newPassword,
            @Parameter(description = "验证码") @RequestParam String verificationCode) {
        Long userId = (Long) request.getAttribute("userId");
        userService.changePassword(userId, oldPassword, newPassword, verificationCode);
        return Result.success();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
