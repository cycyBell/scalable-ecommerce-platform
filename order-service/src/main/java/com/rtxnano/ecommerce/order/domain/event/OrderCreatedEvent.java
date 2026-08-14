package com.rtxnano.ecommerce.order.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain Event published to RabbitMQ (order.exchange) when a new order is created.
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        String shippingAddress,
        List<OrderItemPayload> items,
        Instant createdAt
) {

    public record OrderItemPayload(
            String productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal subtotal
    ) {}
}
