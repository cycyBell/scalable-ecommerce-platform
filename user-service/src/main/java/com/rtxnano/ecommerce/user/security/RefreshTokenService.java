package com.rtxnano.ecommerce.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    // How long a refresh token stays valid, in milliseconds — reused
    // from application.properties, same pattern as the JWT expiration.
    @Value("${app.refresh-token.expiration-ms}")
    private long refreshTokenExpirationMs;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Generates a brand-new refresh token for a given user's email,
    // stores it in Redis with an expiration, and returns the token
    // string to hand back to the client. Called right after login,
    // alongside the JWT access token.
    public String createRefreshToken(String email) {

        // Unlike the JWT access token (which is a signed, self-contained
        // string built by JwtService), a refresh token here is just a
        // random, unguessable string — a UUID. It carries no information
        // in itself; ALL of its meaning comes from what we store next to
        // it in Redis. This is a deliberate difference: the access token
        // proves identity through cryptography alone; the refresh token
        // proves identity through Redis remembering "this exact string
        // belongs to this exact user."
        String refreshToken = UUID.randomUUID().toString();

        // The Redis key is prefixed for clarity and to avoid collisions
        // with other kinds of data we might store in the same Redis
        // instance later (e.g. if Cart Service shared this Redis
        // instance — though in our case each service has its own).
        String key = "refresh_token:" + refreshToken;

        // Store: key -> the user's email (so later, given a refresh
        // token, we can look up WHOSE token it is), with an expiration
        // duration. Redis handles the actual auto-deletion after this
        // time — we never need to write cleanup code ourselves.
        redisTemplate.opsForValue().set(
                key,
                email,
                Duration.ofMillis(refreshTokenExpirationMs)
        );

        return refreshToken;
    }

    // Given a refresh token a client presents, look up whose token it
    // is. Returns null if the token doesn't exist in Redis at all —
    // either it never existed, it expired naturally, or it was
    // explicitly revoked (deleted) via logout.
    public String getEmailFromRefreshToken(String refreshToken) {
        String key = "refresh_token:" + refreshToken;
        return redisTemplate.opsForValue().get(key);
    }

    // Explicitly deletes a refresh token from Redis — this is what
    // "logout" actually means in our system. Once deleted, this exact
    // token can never be used again, immediately, regardless of how
    // much time was left before its natural expiration.
    public void revokeRefreshToken(String refreshToken) {
        String key = "refresh_token:" + refreshToken;
        redisTemplate.delete(key);
    }
}