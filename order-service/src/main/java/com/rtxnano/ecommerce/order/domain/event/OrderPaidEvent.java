package com.rtxnano.ecommerce.order.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event published when an order payment is successfully processed.
 */
public record OrderPaidEvent(
        UUID orderId,
        UUID userId,
        String paymentId,
        Instant paidAt
) {
}
