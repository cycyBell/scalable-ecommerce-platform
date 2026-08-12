package com.rtxnano.ecommerce.order.domain.exception;

import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;

/**
 * Thrown when an order state transition violates the deterministic state machine rules.
 */
public class InvalidOrderStateTransitionException extends RuntimeException {

    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public InvalidOrderStateTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format("Invalid order state transition from '%s' to '%s'", currentStatus, targetStatus));
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}
