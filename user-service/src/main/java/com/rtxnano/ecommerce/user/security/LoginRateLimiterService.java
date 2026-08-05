package com.rtxnano.ecommerce.user.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service implementing Redis-backed rate limiting to defend against brute-force login attempts.
 *
 * Key Educational Concept:
 * Caps failed authentication attempts per identifier (email/IP) to 5 attempts within a 15-minute window.
 * Counters self-heal via Redis TTL expiration.
 */
@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getKey(String identifier) {
        return "rate_limit:login:" + identifier.toLowerCase().trim();
    }

    /**
     * Checks if the given identifier has exceeded the maximum allowed failed login attempts.
     */
    public boolean isRateLimited(String identifier) {
        String key = getKey(identifier);
        String attemptsStr = redisTemplate.opsForValue().get(key);
        if (attemptsStr != null) {
            int attempts = Integer.parseInt(attemptsStr);
            return attempts >= MAX_ATTEMPTS;
        }
        return false;
    }

    /**
     * Increments the failed login attempt counter in Redis.
     */
    public void incrementFailedAttempts(String identifier) {
        String key = getKey(identifier);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
    }

    /**
     * Resets (clears) the failed login attempt counter upon successful login.
     */
    public void resetAttempts(String identifier) {
        String key = getKey(identifier);
        redisTemplate.delete(key);
    }
}
