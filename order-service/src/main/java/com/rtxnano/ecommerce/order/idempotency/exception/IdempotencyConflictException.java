package com.rtxnano.ecommerce.order.idempotency.exception;

/**
 * Thrown when an identical request with the same Idempotency-Key is currently in progress.
 * Translates to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super(String.format("A request with idempotency key '%s' is currently being processed. Please wait for completion.", idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
