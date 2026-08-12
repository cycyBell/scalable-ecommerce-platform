package com.rtxnano.ecommerce.order.domain.enums;

/**
 * Represents the execution state of an API request associated with an Idempotency-Key.
 */
public enum IdempotencyStatus {
    /**
     * Request is actively being processed within an open database transaction.
     */
    PROCESSING,

    /**
     * Request execution completed successfully and response payload is cached for replay.
     */
    COMPLETED
}
