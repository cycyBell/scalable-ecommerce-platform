package com.rtxnano.ecommerce.order.exception;

import java.util.UUID;

/**
 * Thrown when an order is not found by ID.
 * Translates to HTTP 404 Not Found.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super(String.format("Order with ID '%s' was not found", orderId));
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
