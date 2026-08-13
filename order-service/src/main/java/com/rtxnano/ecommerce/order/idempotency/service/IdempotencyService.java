package com.rtxnano.ecommerce.order.idempotency.service;

import com.rtxnano.ecommerce.order.domain.entity.IdempotencyRecord;
import com.rtxnano.ecommerce.order.domain.enums.IdempotencyStatus;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyConflictException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyPayloadMismatchException;
import com.rtxnano.ecommerce.order.idempotency.model.IdempotencyResult;
import com.rtxnano.ecommerce.order.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * ==============================================================================
 * SERVICE: IdempotencyService
 * ==============================================================================
 * Manages distributed idempotency locks, SHA-256 request hashing, response caching,
 * and safe idempotent replay for mutating endpoints (such as POST /orders).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long DEFAULT_TTL_MINUTES = 1440; // 24 Hours

    private final IdempotencyRecordRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Computes a deterministic SHA-256 hex digest of the raw request payload or string.
     */
    public String computeRequestHash(String rawPayload) {
        if (rawPayload == null) {
            rawPayload = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Checks if a request with this key has already been executed or is currently processing.
     * If new, acquires a processing lock in the database.
     *
     * @param key Unique client idempotency key
     * @param userId Authenticated user ID
     * @param requestHash SHA-256 digest of the request
     * @return IdempotencyResult indicating whether to proceed with execution or replay cached response
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult checkOrLock(String key, UUID userId, String requestHash) {
        if (key == null || key.isBlank()) {
            // If no idempotency key is supplied, proceed normally without idempotency caching
            return IdempotencyResult.proceed();
        }

        String sanitizedKey = key.trim();
        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByKeyAndUserId(sanitizedKey, userId);

        if (existingOpt.isPresent()) {
            IdempotencyRecord record = existingOpt.get();

            // If the record has expired, delete it and allow fresh execution
            if (record.isExpired()) {
                log.info("Idempotency record '{}' has expired; removing for fresh execution", sanitizedKey);
                idempotencyRepository.delete(record);
            } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Concurrent duplicate request detected for idempotency key '{}' (PROCESSING)", sanitizedKey);
                throw new IdempotencyConflictException(sanitizedKey);
            } else if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                // Verify that the payload hash matches the original request
                if (!record.getRequestHash().equalsIgnoreCase(requestHash)) {
                    log.error("Idempotency key '{}' re-used with different payload hash (Expected={}, Received={})",
                            sanitizedKey, record.getRequestHash(), requestHash);
                    throw new IdempotencyPayloadMismatchException(sanitizedKey);
                }

                log.info("Idempotency key '{}' resolved to COMPLETED; returning cached response", sanitizedKey);
                return IdempotencyResult.completed(record.getResponseBody());
            }
        }

        // Acquire new processing lock in a separate immediate transaction
        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(sanitizedKey, userId, requestHash, DEFAULT_TTL_MINUTES);
            idempotencyRepository.saveAndFlush(newRecord);
            log.debug("Acquired idempotency lock for key '{}' (PROCESSING)", sanitizedKey);
            return IdempotencyResult.proceed();
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted the key concurrently
            log.warn("Race condition detected inserting idempotency key '{}'", sanitizedKey);
            throw new IdempotencyConflictException(sanitizedKey);
        }
    }

    /**
     * Marks the idempotency record as COMPLETED and caches the serialized response body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, UUID userId, String responseBody) {
        if (key == null || key.isBlank()) {
            return;
        }

        String sanitizedKey = key.trim();
        idempotencyRepository.findByKeyAndUserId(sanitizedKey, userId).ifPresent(record -> {
            record.markAsCompleted(responseBody);
            idempotencyRepository.saveAndFlush(record);
            log.debug("Updated idempotency record '{}' to COMPLETED", sanitizedKey);
        });
    }

    /**
     * Releases/deletes the idempotency record if an unrecoverable business or network error occurred
     * prior to completing the transaction, allowing subsequent retries.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unlock(String key, UUID userId) {
        if (key == null || key.isBlank()) {
            return;
        }

        String sanitizedKey = key.trim();
        idempotencyRepository.findByKeyAndUserId(sanitizedKey, userId).ifPresent(record -> {
            if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                idempotencyRepository.delete(record);
                log.debug("Released PROCESSING lock for idempotency key '{}'", sanitizedKey);
            }
        });
    }
}
