package com.echocampus.bot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户信息请求DTO
 * 用于更新用户个人资料，修改邮箱时需要提供验证码
 *
 * @author EchoCampus Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新用户信息请求")
public class UpdateProfileRequest {

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称", example = "张三")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /**
     * 用户邮箱
     * 如果修改邮箱，需要提供新邮箱的验证码
     */
    @Schema(description = "用户邮箱", example = "user@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 旧邮箱验证码
     * 当修改邮箱时必填，用于验证用户对原邮箱的所有权（防止账号被盗后邮箱被恶意更改）
     */
    @Schema(description = "旧邮箱验证码（修改邮箱时必填）", example = "123456")
    @Size(min = 6, max = 6, message = "验证码必须是6位")
    private String oldEmailVerificationCode;

    /**
     * 新邮箱验证码
     * 当修改邮箱时必填，用于验证用户对新邮箱的所有权
     */
    @Schema(description = "新邮箱验证码（修改邮箱时必填）", example = "654321")
    @Size(min = 6, max = 6, message = "验证码必须是6位")
    private String newEmailVerificationCode;
}
