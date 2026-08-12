package com.rtxnano.ecommerce.order.domain.enums;

/**
 * Represents the strict deterministic lifecycle states of an Order.
 * 
 * VALID LIFECYCLE PATHS:
 * 1. PENDING -> PAID -> SHIPPED -> DELIVERED (Happy Path)
 * 2. PENDING -> CANCELLED (Payment Failed / User Cancellation)
 * 3. PAID -> CANCELLED (Refund / Admin Cancellation prior to shipping)
 */
public enum OrderStatus {
    /**
     * Initial state upon checkout. Cart items snapshot saved, awaiting payment confirmation.
     */
    PENDING,

    /**
     * Payment has succeeded via RabbitMQ PaymentSucceeded event. Ready for fulfillment.
     */
    PAID,

    /**
     * Warehouse fulfillment has dispatched the package to the logistics carrier.
     */
    SHIPPED,

    /**
     * Customer has received the shipment. Terminal successful state.
     */
    DELIVERED,

    /**
     * Order has been aborted or refunded. Terminal cancellation state.
     */
    CANCELLED
}
