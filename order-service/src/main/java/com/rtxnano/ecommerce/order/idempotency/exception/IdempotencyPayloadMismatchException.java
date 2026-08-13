package com.rtxnano.ecommerce.order.idempotency.exception;

/**
 * Thrown when an idempotency key is reused with a different request payload or method.
 * Translates to HTTP 422 Unprocessable Entity (or HTTP 400 Bad Request).
 */
public class IdempotencyPayloadMismatchException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyPayloadMismatchException(String idempotencyKey) {
        super(String.format("Idempotency key '%s' was previously used with a different request payload.", idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
