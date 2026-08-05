package com.rtxnano.ecommerce.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Service managing Redis-backed stateful Refresh Tokens and session revocation.
 *
 * Key Educational Concepts:
 * 1. Refresh Token Rotation: On every refresh request, the old refresh token is revoked
 *    and a new refresh token is issued. This limits the exposure window of stolen tokens.
 * 2. User Session Set: Active refresh tokens are tracked per-user in a Redis Set (user_tokens:<email>),
 *    enabling global session revocation ("Logout All Devices").
 */
@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.refresh-token.expiration-ms}")
    private long refreshTokenExpirationMs;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a brand-new refresh token UUID, maps it to the user's email in Redis,
     * and adds the token UUID to the user's active session set.
     */
    public String createRefreshToken(String email) {
        String refreshToken = UUID.randomUUID().toString();
        String tokenKey = "refresh_token:" + refreshToken;
        String userTokensKey = "user_tokens:" + email;

        Duration ttl = Duration.ofMillis(refreshTokenExpirationMs);

        // Store token -> email mapping
        redisTemplate.opsForValue().set(tokenKey, email, ttl);

        // Add token UUID to user's active session set
        redisTemplate.opsForSet().add(userTokensKey, refreshToken);
        redisTemplate.expire(userTokensKey, ttl);

        return refreshToken;
    }

    /**
     * Look up the email associated with a refresh token.
     */
    public String getEmailFromRefreshToken(String refreshToken) {
        String tokenKey = "refresh_token:" + refreshToken;
        return redisTemplate.opsForValue().get(tokenKey);
    }

    /**
     * Refresh Token Rotation: Invalidates the used refresh token and issues a fresh one.
     */
    public String rotateRefreshToken(String oldRefreshToken) {
        String email = getEmailFromRefreshToken(oldRefreshToken);
        if (email == null) {
            return null;
        }

        // Revoke the old token
        revokeRefreshToken(oldRefreshToken);

        // Issue new refresh token
        return createRefreshToken(email);
    }

    /**
     * Revokes a single refresh token from Redis.
     */
    public void revokeRefreshToken(String refreshToken) {
        String tokenKey = "refresh_token:" + refreshToken;
        String email = redisTemplate.opsForValue().get(tokenKey);

        redisTemplate.delete(tokenKey);

        if (email != null) {
            String userTokensKey = "user_tokens:" + email;
            redisTemplate.opsForSet().remove(userTokensKey, refreshToken);
        }
    }

    /**
     * Revokes ALL active refresh tokens for a user across all devices (Global Logout).
     */
    public void revokeAllUserTokens(String email) {
        String userTokensKey = "user_tokens:" + email;
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey);

        if (tokens != null && !tokens.isEmpty()) {
            for (String token : tokens) {
                redisTemplate.delete("refresh_token:" + token);
            }
        }
        redisTemplate.delete(userTokensKey);
    }
}