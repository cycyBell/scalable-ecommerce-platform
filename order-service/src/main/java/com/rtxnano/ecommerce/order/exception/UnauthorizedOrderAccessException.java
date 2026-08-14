package com.rtxnano.ecommerce.order.exception;

import java.util.UUID;

/**
 * Thrown when a customer attempts to access or modify an order owned by another user.
 * Translates to HTTP 403 Forbidden.
 */
public class UnauthorizedOrderAccessException extends RuntimeException {

    private final UUID orderId;
    private final UUID userId;

    public UnauthorizedOrderAccessException(UUID orderId, UUID userId) {
        super(String.format("User '%s' is not authorized to access order '%s'", userId, orderId));
        this.orderId = orderId;
        this.userId = userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }
}
