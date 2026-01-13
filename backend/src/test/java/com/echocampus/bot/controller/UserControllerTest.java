package com.echocampus.bot.controller;

import com.echocampus.bot.common.Result;
import com.echocampus.bot.common.ResultCode;
import com.echocampus.bot.common.exception.BusinessException;
import com.echocampus.bot.dto.request.LoginRequest;
import com.echocampus.bot.dto.request.RegisterWithCodeRequest;
import com.echocampus.bot.dto.request.SendVerificationCodeRequest;
import com.echocampus.bot.dto.response.LoginResponse;
import com.echocampus.bot.entity.User;
import com.echocampus.bot.service.UserService;
import com.echocampus.bot.service.VerificationCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 控制器测试
 * P2 优先级 - API契约验证
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // 禁用安全过滤器
@DisplayName("UserController - 用户控制器测试")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private VerificationCodeService verificationCodeService;

    private User testUser;
    private LoginRequest loginRequest;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        testUser = createTestUser();
        loginRequest = createLoginRequest();
        loginResponse = createLoginResponse();
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("hashedPassword");
        user.setEmail("test@example.com");
        user.setNickname("测试用户");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private LoginRequest createLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        return request;
    }

    private LoginResponse createLoginResponse() {
        return LoginResponse.builder()
            .userId(1L)
            .username("testuser")
            .nickname("测试用户")
            .email("test@example.com")
            .role("USER")
            .token("test-jwt-token")
            .expireAt(System.currentTimeMillis() + 3600000)
            .build();
    }

    @Nested
    @DisplayName("登录接口测试 - POST /v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("正确凭证应该返回200和Token")
        void shouldReturnTokenForValidCredentials() throws Exception {
            // Arrange
            when(userService.login(any(LoginRequest.class))).thenReturn(loginResponse);

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @DisplayName("用户名为空应该返回400")
        void shouldReturn400ForEmptyUsername() throws Exception {
            // Arrange
            loginRequest.setUsername("");

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码为空应该返回400")
        void shouldReturn400ForEmptyPassword() throws Exception {
            // Arrange
            loginRequest.setPassword("");

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("用户不存在应该返回业务错误")
        void shouldReturnErrorWhenUserNotFound() throws Exception {
            // Arrange
            when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.USER_NOT_FOUND));

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.USER_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("密码错误应该返回业务错误")
        void shouldReturnErrorWhenPasswordWrong() throws Exception {
            // Arrange
            when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.PASSWORD_ERROR));

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PASSWORD_ERROR.getCode()));
        }

        @Test
        @DisplayName("用户名过短应该返回400")
        void shouldReturn400ForShortUsername() throws Exception {
            // Arrange
            loginRequest.setUsername("ab"); // 小于3字符

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("密码过短应该返回400")
        void shouldReturn400ForShortPassword() throws Exception {
            // Arrange
            loginRequest.setPassword("12345"); // 小于6字符

            // Act & Assert
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("注册接口测试 - POST /v1/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("正确信息应该注册成功")
        void shouldRegisterSuccessfully() throws Exception {
            // Arrange
            User newUser = new User();
            newUser.setUsername("newuser");
            newUser.setPassword("password123");
            newUser.setEmail("new@example.com");
            
            when(userService.register(any(User.class))).thenReturn(testUser);

            // Act & Assert
            mockMvc.perform(post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.password").doesNotExist()); // 密码不应返回
        }

        @Test
        @DisplayName("用户名已存在应该返回业务错误")
        void shouldReturnErrorWhenUsernameExists() throws Exception {
            // Arrange
            User newUser = new User();
            newUser.setUsername("existinguser");
            newUser.setPassword("password123");
            
            when(userService.register(any(User.class)))
                .thenThrow(new BusinessException(ResultCode.USER_ALREADY_EXISTS, "用户名已存在"));

            // Act & Assert
            mockMvc.perform(post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.USER_ALREADY_EXISTS.getCode()));
        }
    }

    @Nested
    @DisplayName("发送验证码接口测试 - POST /v1/auth/send-verification-code")
    class SendVerificationCodeTests {

        @Test
        @DisplayName("应该成功发送验证码")
        void shouldSendVerificationCodeSuccessfully() throws Exception {
            // Arrange
            SendVerificationCodeRequest request = new SendVerificationCodeRequest();
            request.setEmail("test@example.com");
            request.setType("REGISTER");
            
            doNothing().when(verificationCodeService).sendVerificationCode(anyString(), anyString(), anyString());

            // Act & Assert
            mockMvc.perform(post("/v1/auth/send-verification-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("带验证码注册接口测试 - POST /v1/auth/register-with-code")
    class RegisterWithCodeTests {

        @Test
        @DisplayName("正确验证码应该注册成功")
        void shouldRegisterWithCodeSuccessfully() throws Exception {
            // Arrange
            RegisterWithCodeRequest request = new RegisterWithCodeRequest();
            request.setUsername("codeuser");
            request.setPassword("password123");
            request.setEmail("code@example.com");
            request.setNickname("验证码用户");
            request.setVerificationCode("123456");

            when(userService.registerWithVerificationCode(
                anyString(), anyString(), anyString(), anyString(), anyString()
            )).thenReturn(testUser);

            // Act & Assert
            mockMvc.perform(post("/v1/auth/register-with-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @DisplayName("验证码无效应该返回业务错误")
        void shouldReturnErrorWhenVerificationCodeInvalid() throws Exception {
            // Arrange
            RegisterWithCodeRequest request = new RegisterWithCodeRequest();
            request.setUsername("codeuser");
            request.setPassword("password123");
            request.setEmail("code@example.com");
            request.setVerificationCode("wrong");

            when(userService.registerWithVerificationCode(
                anyString(), anyString(), anyString(), anyString(), anyString()
            )).thenThrow(new BusinessException(ResultCode.VERIFICATION_CODE_INVALID));

            // Act & Assert
            mockMvc.perform(post("/v1/auth/register-with-code")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.VERIFICATION_CODE_INVALID.getCode()));
        }
    }

    @Nested
    @DisplayName("获取用户信息接口测试 - GET /v1/user/profile")
    class GetProfileTests {

        @Test
        @DisplayName("应该成功获取用户信息")
        void shouldGetProfileSuccessfully() throws Exception {
            // Arrange
            when(userService.getUserById(1L)).thenReturn(testUser);

            // Act & Assert
            mockMvc.perform(get("/v1/user/profile")
                    .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        }
    }

    @Nested
    @DisplayName("更新用户信息接口测试 - PUT /v1/user/profile")
    class UpdateProfileTests {

        @Test
        @DisplayName("应该成功更新用户信息")
        void shouldUpdateProfileSuccessfully() throws Exception {
            // Arrange
            User updateUser = new User();
            updateUser.setNickname("新昵称");
            
            doNothing().when(userService).updateUser(any(User.class));

            // Act & Assert
            mockMvc.perform(put("/v1/user/profile")
                    .requestAttr("userId", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("修改密码接口测试 - PUT /v1/user/password")
    class ChangePasswordTests {

        @Test
        @DisplayName("应该成功修改密码")
        void shouldChangePasswordSuccessfully() throws Exception {
            // Arrange
            doNothing().when(userService).changePassword(anyLong(), anyString(), anyString(), anyString());

            // Act & Assert
            mockMvc.perform(put("/v1/user/password")
                    .requestAttr("userId", 1L)
                    .param("oldPassword", "oldpass")
                    .param("newPassword", "newpass")
                    .param("verificationCode", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("原密码错误应该返回业务错误")
        void shouldReturnErrorWhenOldPasswordWrong() throws Exception {
            // Arrange
            doThrow(new BusinessException(ResultCode.PASSWORD_ERROR, "原密码错误"))
                .when(userService).changePassword(anyLong(), anyString(), anyString(), anyString());

            // Act & Assert
            mockMvc.perform(put("/v1/user/password")
                    .requestAttr("userId", 1L)
                    .param("oldPassword", "wrongpass")
                    .param("newPassword", "newpass")
                    .param("verificationCode", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PASSWORD_ERROR.getCode()));
        }
    }
}
