package com.rtxnano.ecommerce.order.repository;

import com.rtxnano.ecommerce.order.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for IdempotencyRecord entities.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Finds an idempotency record for a specific key and authenticated user.
     */
    Optional<IdempotencyRecord> findByKeyAndUserId(String key, UUID userId);

    /**
     * Purges expired idempotency records.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpiredRecords(@Param("now") Instant now);
}
