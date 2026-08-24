package com.rtxnano.ecommerce.order.domain.entity;

import com.rtxnano.ecommerce.order.domain.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * ==============================================================================
 * DOMAIN ENTITY: IdempotencyRecord
 * ==============================================================================
 * Tracks incoming client idempotency keys to deduplicate mutation requests
 * and safely return cached responses on retries.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "\"key\"", nullable = false, length = 255, updatable = false)
    private String key;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IdempotencyStatus status = IdempotencyStatus.PROCESSING;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, UUID userId, String requestHash, long ttlMinutes) {
        this.key = key;
        this.userId = userId;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.PROCESSING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.expiresAt == null) {
            this.expiresAt = now.plus(24, ChronoUnit.HOURS);
        }
        if (this.status == null) {
            this.status = IdempotencyStatus.PROCESSING;
        }
    }

    public void markAsCompleted(String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseBody = responseBody;
    }

    public boolean isExpired() {
        return this.expiresAt != null && Instant.now().isAfter(this.expiresAt);
    }

    // Getters and Setters

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(IdempotencyStatus status) {
        this.status = status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyRecord that = (IdempotencyRecord) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "IdempotencyRecord{" +
                "key='" + key + '\'' +
                ", userId=" + userId +
                ", requestHash='" + requestHash + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
