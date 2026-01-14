package com.echocampus.bot.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * P0 优先级 - 安全核心组件
 */
@DisplayName("JwtUtil - JWT工具类测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // 测试用密钥（至少256位/32字符）
    private static final String TEST_SECRET = "echocampus-bot-test-secret-key-32chars!";
    private static final Long TEST_EXPIRATION = 3600000L; // 1小时

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION);
    }

    @Nested
    @DisplayName("Token 生成测试")
    class GenerateTokenTests {

        @Test
        @DisplayName("应该成功生成包含正确Claims的Token")
        void shouldGenerateTokenWithCorrectClaims() {
            // Arrange
            Long userId = 123L;
            String username = "testuser";
            String role = "USER";

            // Act
            String token = jwtUtil.generateToken(userId, username, role);

            // Assert
            assertThat(token).isNotNull().isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT格式：header.payload.signature
        }

        @ParameterizedTest
        @DisplayName("应该为不同用户生成不同的Token")
        @CsvSource({
            "1, user1, USER",
            "2, user2, ADMIN",
            "100, admin, ADMIN"
        })
        void shouldGenerateDifferentTokensForDifferentUsers(Long userId, String username, String role) {
            // Act
            String token = jwtUtil.generateToken(userId, username, role);

            // Assert
            assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(userId);
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(username);
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo(role);
        }

        @Test
        @DisplayName("生成的Token应该包含正确的Subject")
        void shouldGenerateTokenWithCorrectSubject() {
            // Arrange
            String username = "testuser";

            // Act
            String token = jwtUtil.generateToken(1L, username, "USER");

            // Assert
            Claims claims = jwtUtil.parseToken(token);
            assertThat(claims.getSubject()).isEqualTo(username);
        }
    }

    @Nested
    @DisplayName("Token 解析测试")
    class ParseTokenTests {

        @Test
        @DisplayName("应该正确解析有效Token")
        void shouldParseValidToken() {
            // Arrange
            Long userId = 456L;
            String username = "parseuser";
            String role = "ADMIN";
            String token = jwtUtil.generateToken(userId, username, role);

            // Act
            Claims claims = jwtUtil.parseToken(token);

            // Assert
            assertThat(claims).isNotNull();
            assertThat(claims.get("userId", Long.class)).isEqualTo(userId);
            assertThat(claims.getSubject()).isEqualTo(username);
            assertThat(claims.get("role", String.class)).isEqualTo(role);
        }

        @Test
        @DisplayName("解析Token应该包含签发时间和过期时间")
        void shouldContainIssuedAtAndExpiration() {
            // Arrange
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Act
            Claims claims = jwtUtil.parseToken(token);

            // Assert
            assertThat(claims.getIssuedAt()).isNotNull();
            assertThat(claims.getExpiration()).isNotNull();
            assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        }
    }

    @Nested
    @DisplayName("Claim 提取测试")
    class ExtractClaimsTests {

        @Test
        @DisplayName("应该正确提取userId")
        void shouldExtractUserId() {
            // Arrange
            Long expectedUserId = 789L;
            String token = jwtUtil.generateToken(expectedUserId, "user", "USER");

            // Act
            Long actualUserId = jwtUtil.getUserIdFromToken(token);

            // Assert
            assertThat(actualUserId).isEqualTo(expectedUserId);
        }

        @Test
        @DisplayName("应该正确提取username")
        void shouldExtractUsername() {
            // Arrange
            String expectedUsername = "extractuser";
            String token = jwtUtil.generateToken(1L, expectedUsername, "USER");

            // Act
            String actualUsername = jwtUtil.getUsernameFromToken(token);

            // Assert
            assertThat(actualUsername).isEqualTo(expectedUsername);
        }

        @Test
        @DisplayName("应该正确提取role")
        void shouldExtractRole() {
            // Arrange
            String expectedRole = "ADMIN";
            String token = jwtUtil.generateToken(1L, "user", expectedRole);

            // Act
            String actualRole = jwtUtil.getRoleFromToken(token);

            // Assert
            assertThat(actualRole).isEqualTo(expectedRole);
        }

        @ParameterizedTest
        @DisplayName("应该正确提取不同角色")
        @ValueSource(strings = {"USER", "ADMIN", "MODERATOR", "GUEST"})
        void shouldExtractDifferentRoles(String role) {
            // Arrange
            String token = jwtUtil.generateToken(1L, "user", role);

            // Act & Assert
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo(role);
        }
    }

    @Nested
    @DisplayName("Token 验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("有效Token应该验证通过")
        void shouldValidateValidToken() {
            // Arrange
            String token = jwtUtil.generateToken(1L, "validuser", "USER");

            // Act & Assert
            assertThat(jwtUtil.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("刚生成的Token不应该过期")
        void shouldNotBeExpiredImmediatelyAfterGeneration() {
            // Arrange
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Act & Assert
            assertThat(jwtUtil.isTokenExpired(token)).isFalse();
        }

        @ParameterizedTest
        @DisplayName("无效Token格式应该验证失败")
        @NullAndEmptySource
        @ValueSource(strings = {
            "invalid",
            "invalid.token",
            "invalid.token.format",
            "eyJhbGciOiJIUzI1NiJ9.invalid.signature"
        })
        void shouldFailValidationForInvalidTokenFormat(String invalidToken) {
            // Act & Assert
            assertThat(jwtUtil.validateToken(invalidToken)).isFalse();
        }

        @Test
        @DisplayName("篡改后的Token应该验证失败")
        void shouldFailValidationForTamperedToken() {
            // Arrange
            String token = jwtUtil.generateToken(1L, "user", "USER");
            String tamperedToken = token.substring(0, token.length() - 5) + "xxxxx";

            // Act & Assert
            assertThat(jwtUtil.validateToken(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("使用不同密钥签名的Token应该验证失败")
        void shouldFailValidationForTokenWithDifferentSecret() {
            // Arrange - 使用当前jwtUtil生成token
            String token = jwtUtil.generateToken(1L, "user", "USER");
            
            // 创建使用不同密钥的jwtUtil
            JwtUtil otherJwtUtil = new JwtUtil();
            ReflectionTestUtils.setField(otherJwtUtil, "secret", "different-secret-key-32-characters!");
            ReflectionTestUtils.setField(otherJwtUtil, "expiration", TEST_EXPIRATION);

            // Act & Assert - 用不同密钥验证应该失败
            assertThat(otherJwtUtil.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Token 过期测试")
    class TokenExpirationTests {

        @Test
        @DisplayName("过期Token应该验证失败")
        void shouldFailValidationForExpiredToken() {
            // Arrange - 设置极短过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", 1L); // 1毫秒
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Act - 等待过期
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Assert
            assertThat(jwtUtil.validateToken(token)).isFalse();
            assertThat(jwtUtil.isTokenExpired(token)).isTrue();
        }

        @Test
        @DisplayName("未过期Token应该验证成功")
        void shouldPassValidationForNotExpiredToken() {
            // Arrange - 设置足够长的过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L); // 1小时
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Act & Assert
            assertThat(jwtUtil.validateToken(token)).isTrue();
            assertThat(jwtUtil.isTokenExpired(token)).isFalse();
        }

        @ParameterizedTest
        @DisplayName("不同过期时间的Token应该正确处理")
        @ValueSource(longs = {1000L, 60000L, 3600000L, 86400000L})
        void shouldHandleDifferentExpirationTimes(Long expiration) {
            // Arrange
            ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);

            // Act
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Assert
            assertThat(jwtUtil.validateToken(token)).isTrue();
            Claims claims = jwtUtil.parseToken(token);
            long expectedExpireTime = claims.getIssuedAt().getTime() + expiration;
            assertThat(claims.getExpiration().getTime()).isEqualTo(expectedExpireTime);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("解析null Token应该抛出异常")
        void shouldThrowExceptionForNullToken() {
            // Act & Assert
            assertThatThrownBy(() -> jwtUtil.parseToken(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("解析空Token应该抛出异常")
        void shouldThrowExceptionForEmptyToken() {
            // Act & Assert
            assertThatThrownBy(() -> jwtUtil.parseToken(""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("解析格式错误的Token应该抛出异常")
        void shouldThrowExceptionForMalformedToken() {
            // Act & Assert
            assertThatThrownBy(() -> jwtUtil.parseToken("malformed.token"))
                .isInstanceOf(MalformedJwtException.class);
        }

        @Test
        @DisplayName("解析签名错误的Token应该抛出异常")
        void shouldThrowExceptionForInvalidSignature() {
            // Arrange
            String token = jwtUtil.generateToken(1L, "user", "USER");
            String invalidSignatureToken = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

            // Act & Assert
            assertThatThrownBy(() -> jwtUtil.parseToken(invalidSignatureToken))
                .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("过期Token解析应该抛出ExpiredJwtException")
        void shouldThrowExpiredJwtExceptionForExpiredToken() {
            // Arrange - 设置极短过期时间
            ReflectionTestUtils.setField(jwtUtil, "expiration", 1L);
            String token = jwtUtil.generateToken(1L, "user", "USER");

            // Wait for expiration
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Act & Assert
            assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class BoundaryConditionTests {

        @Test
        @DisplayName("应该处理最大Long类型的userId")
        void shouldHandleMaxLongUserId() {
            // Arrange
            Long maxUserId = Long.MAX_VALUE;

            // Act
            String token = jwtUtil.generateToken(maxUserId, "user", "USER");

            // Assert
            assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(maxUserId);
        }

        @Test
        @DisplayName("应该处理特殊字符的用户名")
        void shouldHandleSpecialCharactersInUsername() {
            // Arrange
            String specialUsername = "user@test.com";

            // Act
            String token = jwtUtil.generateToken(1L, specialUsername, "USER");

            // Assert
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(specialUsername);
        }

        @Test
        @DisplayName("应该处理中文用户名")
        void shouldHandleChineseUsername() {
            // Arrange
            String chineseUsername = "测试用户";

            // Act
            String token = jwtUtil.generateToken(1L, chineseUsername, "USER");

            // Assert
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(chineseUsername);
        }

        @Test
        @DisplayName("应该处理长用户名")
        void shouldHandleLongUsername() {
            // Arrange
            String longUsername = "a".repeat(100);

            // Act
            String token = jwtUtil.generateToken(1L, longUsername, "USER");

            // Assert
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(longUsername);
        }

        @Test
        @DisplayName("getExpiration应该返回正确的过期时间")
        void shouldReturnCorrectExpiration() {
            // Act & Assert
            assertThat(jwtUtil.getExpiration()).isEqualTo(TEST_EXPIRATION);
        }
    }
}
