package com.echocampus.bot.config;

import lombok.extern.slf4j.Slf4j;
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

    private final ConcurrentMap<Long, AtomicInteger> userRequestCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalConcurrentRequests = new AtomicInteger(0);
    private final AtomicInteger totalSseConnections = new AtomicInteger(0);

    @Bean
    public RateLimiter rateLimiter() {
        return new RateLimiterImpl();
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
}
