package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OutboxStatus;
import com.rtxnano.ecommerce.order.outbox.OutboxEventPublisher;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxEventPublisher Unit Tests")
class OutboxEventPublisherTests {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private AmqpTemplate amqpTemplate;

    private OutboxEventPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxEventPublisher(outboxRepository, amqpTemplate);
        ReflectionTestUtils.setField(outboxPublisher, "orderExchange", "order.exchange");
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 50);
        ReflectionTestUtils.setField(outboxPublisher, "maxRetryAttempts", 5);
        ReflectionTestUtils.setField(outboxPublisher, "orderCreatedRoutingKey", "order.created");
        ReflectionTestUtils.setField(outboxPublisher, "orderPaidRoutingKey", "order.paid");
        ReflectionTestUtils.setField(outboxPublisher, "orderCancelledRoutingKey", "order.cancelled");
        ReflectionTestUtils.setField(outboxPublisher, "orderShippedRoutingKey", "order.shipped");
        ReflectionTestUtils.setField(outboxPublisher, "orderDeliveredRoutingKey", "order.delivered");
    }

    @Test
    @DisplayName("Should do nothing when no pending outbox events exist")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        outboxPublisher.publishPendingEvents();

        verify(amqpTemplate, never()).send(any(String.class), any(String.class), any(Message.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should publish pending event to RabbitMQ and mark as PUBLISHED")
    void shouldPublishPendingEventSuccessfully() {
        UUID eventId = UUID.randomUUID();
        String orderId = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent("Order", orderId, "OrderCreated", "{\"orderId\":\"" + orderId + "\"}");
        event.setId(eventId);
        event.setCreatedAt(Instant.now());

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));

        outboxPublisher.publishPendingEvents();

        // Verify message sent to RabbitMQ
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(amqpTemplate).send(eq("order.exchange"), eq("order.created"), messageCaptor.capture());

        Message capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
        assertEquals(eventId.toString(), capturedMessage.getMessageProperties().getMessageId());
        assertEquals("OrderCreated", capturedMessage.getMessageProperties().getHeaders().get("eventType"));
        assertEquals("Order", capturedMessage.getMessageProperties().getHeaders().get("aggregateType"));
        assertEquals(orderId, capturedMessage.getMessageProperties().getHeaders().get("aggregateId"));
        assertEquals("application/json", capturedMessage.getMessageProperties().getContentType());

        // Verify event updated to PUBLISHED
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("Should correctly resolve routing keys for all domain events")
    void shouldResolveRoutingKeysCorrectly() {
        assertEquals("order.created", outboxPublisher.resolveRoutingKey("OrderCreated"));
        assertEquals("order.paid", outboxPublisher.resolveRoutingKey("OrderPaid"));
        assertEquals("order.cancelled", outboxPublisher.resolveRoutingKey("OrderCancelled"));
        assertEquals("order.shipped", outboxPublisher.resolveRoutingKey("OrderShipped"));
        assertEquals("order.delivered", outboxPublisher.resolveRoutingKey("OrderDelivered"));
        assertEquals("order.custom", outboxPublisher.resolveRoutingKey("Custom"));
        assertEquals("order.unknown", outboxPublisher.resolveRoutingKey(null));
    }

    @Test
    @DisplayName("Should increment retry count on broker publish failure")
    void shouldIncrementRetryCountOnPublishFailure() {
        OutboxEvent event = new OutboxEvent("Order", "ord-1", "OrderCreated", "{}");
        event.setId(UUID.randomUUID());
        event.setCreatedAt(Instant.now());
        event.setRetryCount(0);

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));

        doThrow(new AmqpException("Connection refused")).when(amqpTemplate).send(any(String.class), any(String.class), any(Message.class));

        outboxPublisher.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("Should mark event as FAILED when max retries are reached")
    void shouldMarkAsFailedWhenMaxRetriesExceeded() {
        OutboxEvent event = new OutboxEvent("Order", "ord-1", "OrderCreated", "{}");
        event.setId(UUID.randomUUID());
        event.setCreatedAt(Instant.now());
        event.setRetryCount(4); // 4 + 1 = 5 (maxRetryAttempts)

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));

        doThrow(new AmqpException("Broker unreachable")).when(amqpTemplate).send(any(String.class), any(String.class), any(Message.class));

        outboxPublisher.publishPendingEvents();

        assertEquals(5, event.getRetryCount());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        verify(outboxRepository).save(event);
    }

    @Test
    @DisplayName("Should process multiple events in FIFO sequence")
    void shouldProcessMultipleEventsInBatch() {
        OutboxEvent event1 = new OutboxEvent("Order", "ord-1", "OrderCreated", "{}");
        event1.setId(UUID.randomUUID());
        OutboxEvent event2 = new OutboxEvent("Order", "ord-2", "OrderPaid", "{}");
        event2.setId(UUID.randomUUID());

        when(outboxRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event1, event2));

        outboxPublisher.publishPendingEvents();

        verify(amqpTemplate, times(2)).send(any(String.class), any(String.class), any(Message.class));
        assertEquals(OutboxStatus.PUBLISHED, event1.getStatus());
        assertEquals(OutboxStatus.PUBLISHED, event2.getStatus());
        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }
}
