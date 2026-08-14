package com.rtxnano.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published to RabbitMQ (order.exchange) when an order is cancelled.
 */
public record OrderCancelledEvent(
        UUID orderId,
        UUID userId,
        String reason,
        Instant cancelledAt
) {
}
