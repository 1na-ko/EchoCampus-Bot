package com.echocampus.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class RateLimitConfig {

    private static final int MAX_CONCURRENT_REQUESTS = 100;
    private static final int MAX_SSE_CONNECTIONS = 50;

    @Value("${verification-code.send-interval-seconds:60}")
    private int sendIntervalSeconds;

    @Value("${verification-code.max-send-per-day:5}")
    private int maxSendPerDay;

    @Value("${verification-code.register-interval-seconds:3600}")
    private int registerIntervalSeconds;

    @Value("${verification-code.max-register-per-hour:3}")
    private int maxRegisterPerHour;

    private final ConcurrentMap<Long, AtomicInteger> userRequestCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalConcurrentRequests = new AtomicInteger(0);
    private final AtomicInteger totalSseConnections = new AtomicInteger(0);

    @Bean
    public RateLimiter rateLimiter() {
        return new RateLimiterImpl();
    }

    @Bean
    public TimeWindowRateLimiter timeWindowRateLimiter() {
        return new TimeWindowRateLimiterImpl();
    }

    public interface RateLimiter {
        boolean tryAcquire(Long userId);
        void release(Long userId);
        boolean tryAcquireSse();
        void releaseSse();
        int getCurrentRequestCount();
        int getCurrentSseCount();
    }

    public class RateLimiterImpl implements RateLimiter {
        @Override
        public boolean tryAcquire(Long userId) {
            int current = totalConcurrentRequests.incrementAndGet();
            if (current > MAX_CONCURRENT_REQUESTS) {
                totalConcurrentRequests.decrementAndGet();
                log.warn("请求限流: 当前并发数={}, 最大并发数={}", current, MAX_CONCURRENT_REQUESTS);
                return false;
            }
            
            userRequestCounters.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
            log.debug("用户 {} 请求获取成功, 当前总并发数={}", userId, current);
            return true;
        }

        @Override
        public void release(Long userId) {
            totalConcurrentRequests.decrementAndGet();
            AtomicInteger counter = userRequestCounters.get(userId);
            if (counter != null) {
                int count = counter.decrementAndGet();
                if (count <= 0) {
                    userRequestCounters.remove(userId);
                }
            }
            log.debug("用户 {} 请求释放, 当前总并发数={}", userId, totalConcurrentRequests.get());
        }

        @Override
        public boolean tryAcquireSse() {
            int current = totalSseConnections.incrementAndGet();
            if (current > MAX_SSE_CONNECTIONS) {
                totalSseConnections.decrementAndGet();
                log.warn("SSE连接限流: 当前连接数={}, 最大连接数={}", current, MAX_SSE_CONNECTIONS);
                return false;
            }
            log.debug("SSE连接获取成功, 当前总连接数={}", current);
            return true;
        }

        @Override
        public void releaseSse() {
            totalSseConnections.decrementAndGet();
            log.debug("SSE连接释放, 当前总连接数={}", totalSseConnections.get());
        }

        @Override
        public int getCurrentRequestCount() {
            return totalConcurrentRequests.get();
        }

        @Override
        public int getCurrentSseCount() {
            return totalSseConnections.get();
        }
    }

    public interface TimeWindowRateLimiter {
        boolean tryAcquireVerificationCode(String email);

        boolean tryAcquireRegister(String ipAddress);
    }

    public class TimeWindowRateLimiterImpl implements TimeWindowRateLimiter {
        private final ConcurrentMap<String, Long> emailLastSendTime = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AtomicInteger> emailDailyCount = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Long> ipLastRegisterTime = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, AtomicInteger> ipHourlyCount = new ConcurrentHashMap<>();

        @Override
        public boolean tryAcquireVerificationCode(String email) {
            long now = System.currentTimeMillis();

            Long lastSendTime = emailLastSendTime.get(email);
            if (lastSendTime != null && (now - lastSendTime) < sendIntervalSeconds * 1000L) {
                log.warn("验证码发送限流: email={}, 距离上次发送不足{}秒", email, sendIntervalSeconds);
                return false;
            }

            AtomicInteger dailyCount = emailDailyCount.computeIfAbsent(email, k -> new AtomicInteger(0));
            if (dailyCount.get() >= maxSendPerDay) {
                log.warn("验证码发送限流: email={}, 今日发送次数已达上限({}次)", email, maxSendPerDay);
                return false;
            }

            emailLastSendTime.put(email, now);
            dailyCount.incrementAndGet();
            log.info("验证码发送成功: email={}, 今日已发送{}次", email, dailyCount.get());
            return true;
        }

        @Override
        public boolean tryAcquireRegister(String ipAddress) {
            long now = System.currentTimeMillis();

            Long lastRegisterTime = ipLastRegisterTime.get(ipAddress);
            if (lastRegisterTime != null && (now - lastRegisterTime) < registerIntervalSeconds * 1000L) {
                log.warn("注册限流: ip={}, 距离上次注册不足{}秒", ipAddress, registerIntervalSeconds);
                return false;
            }

            AtomicInteger hourlyCount = ipHourlyCount.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
            if (hourlyCount.get() >= maxRegisterPerHour) {
                log.warn("注册限流: ip={}, 每小时注册次数已达上限({}次)", ipAddress, maxRegisterPerHour);
                return false;
            }

            ipLastRegisterTime.put(ipAddress, now);
            hourlyCount.incrementAndGet();
            log.info("注册成功: ip={}, 本小时已注册{}次", ipAddress, hourlyCount.get());
            return true;
        }
    }
}
