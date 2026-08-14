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
 * SERVICE IMPLEMENTATION: IdempotencyServiceImpl
 * ==============================================================================
 * Manages distributed idempotency locks, SHA-256 request hashing, response caching,
 * and safe idempotent replay for mutating endpoints (such as POST /orders).
 */
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyServiceImpl.class);
    private static final long DEFAULT_TTL_MINUTES = 1440; // 24 Hours

    private final IdempotencyRecordRepository idempotencyRepository;

    public IdempotencyServiceImpl(IdempotencyRecordRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Override
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult checkOrLock(String key, UUID userId, String requestHash) {
        if (key == null || key.isBlank()) {
            return IdempotencyResult.proceed();
        }

        String sanitizedKey = key.trim();
        Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findByKeyAndUserId(sanitizedKey, userId);

        if (existingOpt.isPresent()) {
            IdempotencyRecord record = existingOpt.get();

            if (record.isExpired()) {
                log.info("Idempotency record '{}' has expired; removing for fresh execution", sanitizedKey);
                idempotencyRepository.delete(record);
            } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Concurrent duplicate request detected for idempotency key '{}' (PROCESSING)", sanitizedKey);
                throw new IdempotencyConflictException(sanitizedKey);
            } else if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                if (!record.getRequestHash().equalsIgnoreCase(requestHash)) {
                    log.error("Idempotency key '{}' re-used with different payload hash (Expected={}, Received={})",
                            sanitizedKey, record.getRequestHash(), requestHash);
                    throw new IdempotencyPayloadMismatchException(sanitizedKey);
                }

                log.info("Idempotency key '{}' resolved to COMPLETED; returning cached response", sanitizedKey);
                return IdempotencyResult.completed(record.getResponseBody());
            }
        }

        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(sanitizedKey, userId, requestHash, DEFAULT_TTL_MINUTES);
            idempotencyRepository.saveAndFlush(newRecord);
            log.debug("Acquired idempotency lock for key '{}' (PROCESSING)", sanitizedKey);
            return IdempotencyResult.proceed();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Race condition detected inserting idempotency key '{}'", sanitizedKey);
            throw new IdempotencyConflictException(sanitizedKey);
        }
    }

    @Override
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

    @Override
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
