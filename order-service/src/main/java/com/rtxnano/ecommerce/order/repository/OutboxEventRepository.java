package com.rtxnano.ecommerce.order.repository;

import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for OutboxEvent entities.
 * Supports high-throughput FIFO polling for the Transactional Outbox pattern.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds pending outbox events ordered by creation time (FIFO) with page limit.
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * Counts events currently in a specific status (e.g. monitoring PENDING lag).
     */
    long countByStatus(OutboxStatus status);

    /**
     * Purges old published events to keep outbox table lean.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.publishedAt < :cutoff")
    int deleteByStatusAndPublishedAtBefore(@Param("status") OutboxStatus status, @Param("cutoff") Instant cutoff);
}
