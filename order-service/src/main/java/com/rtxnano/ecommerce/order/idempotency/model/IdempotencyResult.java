package com.rtxnano.ecommerce.order.idempotency.model;

/**
 * Encapsulates the outcome of checking an idempotency key before request execution.
 */
public record IdempotencyResult(
        boolean shouldProceed,
        boolean isCompleted,
        String cachedResponse
) {

    public static IdempotencyResult proceed() {
        return new IdempotencyResult(true, false, null);
    }

    public static IdempotencyResult completed(String cachedResponse) {
        return new IdempotencyResult(false, true, cachedResponse);
    }
}
