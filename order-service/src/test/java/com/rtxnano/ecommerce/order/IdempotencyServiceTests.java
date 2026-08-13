package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.domain.entity.IdempotencyRecord;
import com.rtxnano.ecommerce.order.domain.enums.IdempotencyStatus;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyConflictException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyPayloadMismatchException;
import com.rtxnano.ecommerce.order.idempotency.model.IdempotencyResult;
import com.rtxnano.ecommerce.order.idempotency.service.IdempotencyService;
import com.rtxnano.ecommerce.order.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Unit Tests")
class IdempotencyServiceTests {

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository);
    }

    @Test
    @DisplayName("Should proceed for fresh key and acquire PROCESSING lock")
    void shouldAcquireLockForFreshKey() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();
        String hash = idempotencyService.computeRequestHash("{\"address\":\"123 Main St\"}");

        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.empty());

        IdempotencyResult result = idempotencyService.checkOrLock(key, userId, hash);

        assertTrue(result.shouldProceed());
        assertFalse(result.isCompleted());
        assertNull(result.cachedResponse());

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRepository).saveAndFlush(captor.capture());

        IdempotencyRecord saved = captor.getValue();
        assertEquals(key, saved.getKey());
        assertEquals(userId, saved.getUserId());
        assertEquals(hash, saved.getRequestHash());
        assertEquals(IdempotencyStatus.PROCESSING, saved.getStatus());
    }

    @Test
    @DisplayName("Should proceed immediately if no idempotency key is provided")
    void shouldProceedWhenKeyIsNull() {
        UUID userId = UUID.randomUUID();
        IdempotencyResult result = idempotencyService.checkOrLock(null, userId, "hash");

        assertTrue(result.shouldProceed());
        assertFalse(result.isCompleted());
        verify(idempotencyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when key status is PROCESSING")
    void shouldThrowConflictWhenProcessing() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();
        String hash = "hash-123";

        IdempotencyRecord record = new IdempotencyRecord(key, userId, hash, 60);
        record.setStatus(IdempotencyStatus.PROCESSING);

        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.of(record));

        IdempotencyConflictException ex = assertThrows(IdempotencyConflictException.class,
                () -> idempotencyService.checkOrLock(key, userId, hash));

        assertEquals(key, ex.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return cached response when key status is COMPLETED and hash matches")
    void shouldReturnCachedResponseWhenCompleted() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();
        String hash = "hash-123";
        String cachedJson = "{\"orderId\":\"order-789\",\"status\":\"PENDING\"}";

        IdempotencyRecord record = new IdempotencyRecord(key, userId, hash, 60);
        record.markAsCompleted(cachedJson);

        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.of(record));

        IdempotencyResult result = idempotencyService.checkOrLock(key, userId, hash);

        assertFalse(result.shouldProceed());
        assertTrue(result.isCompleted());
        assertEquals(cachedJson, result.cachedResponse());
    }

    @Test
    @DisplayName("Should throw IdempotencyPayloadMismatchException when key reused with different body")
    void shouldThrowMismatchWhenHashDiffers() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();
        String originalHash = "original-hash-123";
        String differentHash = "different-hash-456";

        IdempotencyRecord record = new IdempotencyRecord(key, userId, originalHash, 60);
        record.markAsCompleted("{\"orderId\":\"order-789\"}");

        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.of(record));

        IdempotencyPayloadMismatchException ex = assertThrows(IdempotencyPayloadMismatchException.class,
                () -> idempotencyService.checkOrLock(key, userId, differentHash));

        assertEquals(key, ex.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should complete record and cache serialized response")
    void shouldCompleteRecordSuccessfully() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();
        String responseBody = "{\"orderId\":\"order-111\"}";

        IdempotencyRecord record = new IdempotencyRecord(key, userId, "hash", 60);
        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.of(record));

        idempotencyService.complete(key, userId, responseBody);

        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertEquals(responseBody, record.getResponseBody());
        verify(idempotencyRepository).saveAndFlush(record);
    }

    @Test
    @DisplayName("Should unlock and remove PROCESSING record on failure")
    void shouldUnlockProcessingRecord() {
        String key = "key-uuid-123";
        UUID userId = UUID.randomUUID();

        IdempotencyRecord record = new IdempotencyRecord(key, userId, "hash", 60);
        record.setStatus(IdempotencyStatus.PROCESSING);
        when(idempotencyRepository.findByKeyAndUserId(key, userId)).thenReturn(Optional.of(record));

        idempotencyService.unlock(key, userId);

        verify(idempotencyRepository).delete(record);
    }

    @Test
    @DisplayName("Should compute deterministic SHA-256 hash")
    void shouldComputeConsistentSha256Hash() {
        String payload = "{\"shippingAddress\":\"456 Elm St\",\"currency\":\"USD\"}";
        String hash1 = idempotencyService.computeRequestHash(payload);
        String hash2 = idempotencyService.computeRequestHash(payload);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }
}
