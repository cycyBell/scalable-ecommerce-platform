package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.domain.entity.IdempotencyRecord;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.IdempotencyStatus;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.enums.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Order Domain Entities Unit Tests")
class OrderEntityTests {

    @Test
    @DisplayName("Order should correctly recalculate totalAmount when items are added or removed")
    void shouldRecalculateTotalAmount() {
        UUID userId = UUID.randomUUID();
        Order order = new Order(null, userId, "123 Main St, New York, NY", "USD");

        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
        assertEquals(OrderStatus.PENDING, order.getStatus());

        OrderItem item1 = new OrderItem("prod-1", "Mechanical Keyboard", new BigDecimal("120.50"), 2);
        assertEquals(new BigDecimal("241.00"), item1.getSubtotal());

        OrderItem item2 = new OrderItem("prod-2", "Wireless Mouse", new BigDecimal("49.99"), 1);
        assertEquals(new BigDecimal("49.99"), item2.getSubtotal());

        order.addItem(item1);
        assertEquals(1, order.getItems().size());
        assertEquals(new BigDecimal("241.00"), order.getTotalAmount());
        assertEquals(order, item1.getOrder());

        order.addItem(item2);
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("290.99"), order.getTotalAmount());

        order.removeItem(item1);
        assertEquals(1, order.getItems().size());
        assertEquals(new BigDecimal("49.99"), order.getTotalAmount());
        assertNull(item1.getOrder());
    }

    @Test
    @DisplayName("OutboxEvent should manage status transitions and retry count")
    void shouldManageOutboxEventLifecycle() {
        OutboxEvent event = new OutboxEvent("ORDER", "order-123", "OrderCreated", "{\"orderId\":\"order-123\"}");

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getPublishedAt());

        event.incrementRetryCount();
        assertEquals(1, event.getRetryCount());

        event.markAsPublished();
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());

        event.markAsFailed();
        assertEquals(OutboxStatus.FAILED, event.getStatus());
    }

    @Test
    @DisplayName("IdempotencyRecord should manage processing to completed state transition")
    void shouldManageIdempotencyRecordLifecycle() {
        UUID userId = UUID.randomUUID();
        IdempotencyRecord record = new IdempotencyRecord("idem-key-123", userId, "hash-abc-123", 60);

        assertEquals(IdempotencyStatus.PROCESSING, record.getStatus());
        assertEquals("idem-key-123", record.getKey());
        assertEquals(userId, record.getUserId());
        assertNull(record.getResponseBody());
        assertFalse(record.isExpired());

        record.markAsCompleted("{\"status\":\"success\",\"orderId\":\"order-456\"}");
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertEquals("{\"status\":\"success\",\"orderId\":\"order-456\"}", record.getResponseBody());
    }
}
