package com.rtxnano.ecommerce.order.idempotency.service;

import com.rtxnano.ecommerce.order.idempotency.model.IdempotencyResult;

import java.util.UUID;

/**
 * Service interface for managing distributed idempotency locks, request hashing, and response caching.
 */
public interface IdempotencyService {

    /**
     * Computes a deterministic SHA-256 hex digest of the raw request payload or string.
     */
    String computeRequestHash(String rawPayload);

    /**
     * Checks if a request with this key has already been executed or is currently processing.
     */
    IdempotencyResult checkOrLock(String key, UUID userId, String requestHash);

    /**
     * Marks the idempotency record as COMPLETED and caches the serialized response body.
     */
    void complete(String key, UUID userId, String responseBody);

    /**
     * Releases/deletes the idempotency record if an unrecoverable error occurred.
     */
    void unlock(String key, UUID userId);
}
