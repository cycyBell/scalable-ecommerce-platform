package com.rtxnano.ecommerce.order.domain.enums;

/**
 * Represents the delivery status of an OutboxEvent in the Transactional Outbox Pattern.
 */
public enum OutboxStatus {
    /**
     * Event written atomically with the Order entity, awaiting asynchronous publication to RabbitMQ.
     */
    PENDING,

    /**
     * Event successfully acknowledged by the RabbitMQ topic exchange broker.
     */
    PUBLISHED,

    /**
     * Publication failed after reaching maximum configured retry threshold.
     */
    FAILED
}
