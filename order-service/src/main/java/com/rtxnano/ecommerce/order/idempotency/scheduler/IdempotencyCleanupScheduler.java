package com.rtxnano.ecommerce.order.idempotency.scheduler;

import com.rtxnano.ecommerce.order.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ==============================================================================
 * SCHEDULER: IdempotencyCleanupScheduler
 * ==============================================================================
 * Periodically deletes expired idempotency records beyond their 24-hour TTL,
 * preventing unbounded table growth.
 */
@Component
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyRecordRepository idempotencyRepository;

    public IdempotencyCleanupScheduler(IdempotencyRecordRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Executes once every hour to purge records where expires_at < NOW().
     */
    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredRecords() {
        Instant now = Instant.now();
        int deletedCount = idempotencyRepository.deleteExpiredRecords(now);
        if (deletedCount > 0) {
            log.info("Purged {} expired idempotency records at {}", deletedCount, now);
        }
    }
}
