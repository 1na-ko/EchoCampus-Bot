package com.echocampus.bot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;
    private RateLimitConfig.RateLimiter rateLimiter;
    private RateLimitConfig.TimeWindowRateLimiter timeWindowRateLimiter;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        ReflectionTestUtils.setField(rateLimitConfig, "sendIntervalSeconds", 60);
        ReflectionTestUtils.setField(rateLimitConfig, "maxSendPerDay", 5);
        ReflectionTestUtils.setField(rateLimitConfig, "registerIntervalSeconds", 3600);
        ReflectionTestUtils.setField(rateLimitConfig, "maxRegisterPerHour", 3);
        ReflectionTestUtils.setField(rateLimitConfig, "cleanupIntervalHours", 1);

        rateLimiter = rateLimitConfig.new RateLimiterImpl();
        timeWindowRateLimiter = rateLimitConfig.new TimeWindowRateLimiterImpl();
    }

    @Test
    void testRateLimiter_TryAcquire_ShouldReturnTrue() {
        boolean result = rateLimiter.tryAcquire(1L);

        assertTrue(result);
        assertEquals(1, rateLimiter.getCurrentRequestCount());
    }

    @Test
    void testRateLimiter_Release_ShouldDecreaseCount() {
        rateLimiter.tryAcquire(1L);
        rateLimiter.release(1L);

        assertEquals(0, rateLimiter.getCurrentRequestCount());
    }

    @Test
    void testRateLimiter_TryAcquireSse_ShouldReturnTrue() {
        boolean result = rateLimiter.tryAcquireSse();

        assertTrue(result);
        assertEquals(1, rateLimiter.getCurrentSseCount());
    }

    @Test
    void testRateLimiter_ReleaseSse_ShouldDecreaseCount() {
        rateLimiter.tryAcquireSse();
        rateLimiter.releaseSse();

        assertEquals(0, rateLimiter.getCurrentSseCount());
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireVerificationCode_ShouldReturnTrue() {
        boolean result = timeWindowRateLimiter.tryAcquireVerificationCode("test@example.com");

        assertTrue(result);
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireVerificationCode_WithinInterval_ShouldReturnFalse() {
        timeWindowRateLimiter.tryAcquireVerificationCode("test@example.com");
        boolean result = timeWindowRateLimiter.tryAcquireVerificationCode("test@example.com");

        assertFalse(result);
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireVerificationCode_ExceedDailyLimit_ShouldReturnFalse() {
        for (int i = 0; i < 5; i++) {
            timeWindowRateLimiter.tryAcquireVerificationCode("test@example.com");
        }
        boolean result = timeWindowRateLimiter.tryAcquireVerificationCode("test@example.com");

        assertFalse(result);
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireRegister_ShouldReturnTrue() {
        boolean result = timeWindowRateLimiter.tryAcquireRegister("192.168.1.1");

        assertTrue(result);
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireRegister_WithinInterval_ShouldReturnFalse() {
        timeWindowRateLimiter.tryAcquireRegister("192.168.1.1");
        boolean result = timeWindowRateLimiter.tryAcquireRegister("192.168.1.1");

        assertFalse(result);
    }

    @Test
    void testTimeWindowRateLimiter_TryAcquireRegister_ExceedHourlyLimit_ShouldReturnFalse() {
        for (int i = 0; i < 3; i++) {
            timeWindowRateLimiter.tryAcquireRegister("192.168.1.1");
        }
        boolean result = timeWindowRateLimiter.tryAcquireRegister("192.168.1.1");

        assertFalse(result);
    }

    @Test
    void testCleanupExpiredData_ShouldNotThrowException() {
        assertDoesNotThrow(() -> {
            rateLimitConfig.cleanupExpiredData();
        });
    }
}
