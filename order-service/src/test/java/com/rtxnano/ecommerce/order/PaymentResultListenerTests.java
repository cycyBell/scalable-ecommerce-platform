package com.rtxnano.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.dto.PaymentResultEventDto;
import com.rtxnano.ecommerce.order.listener.PaymentResultListener;
import com.rtxnano.ecommerce.order.repository.OrderRepository;
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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentResultListener Unit Tests")
class PaymentResultListenerTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CatalogServiceClient catalogServiceClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private PaymentResultListener paymentResultListener;

    private UUID testOrderId;
    private UUID testUserId;
    private Order testOrder;
    private final long testDeliveryTag = 42L;

    private Channel proxyChannel;
    private Long recordedAckTag;
    private Boolean recordedAckMultiple;
    private Long recordedNackTag;
    private Boolean recordedNackMultiple;
    private Boolean recordedNackRequeue;

    @BeforeEach
    void setUp() {
        paymentResultListener = new PaymentResultListener(
                orderRepository,
                catalogServiceClient,
                outboxEventRepository,
                objectMapper
        );

        testOrderId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testOrder = new Order(testOrderId, testUserId, "123 Market St", "USD");
        testOrder.setStatus(OrderStatus.PENDING);

        recordedAckTag = null;
        recordedAckMultiple = null;
        recordedNackTag = null;
        recordedNackMultiple = null;
        recordedNackRequeue = null;

        // Dynamic Proxy for RabbitMQ Channel to avoid bytecode manipulation issues
        proxyChannel = (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class},
                (proxy, method, args) -> {
                    if ("basicAck".equals(method.getName())) {
                        recordedAckTag = (Long) args[0];
                        recordedAckMultiple = (Boolean) args[1];
                        return null;
                    } else if ("basicNack".equals(method.getName())) {
                        recordedNackTag = (Long) args[0];
                        recordedNackMultiple = (Boolean) args[1];
                        recordedNackRequeue = (Boolean) args[2];
                        return null;
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Should handle payment SUCCESS, transition order to PAID, and record outbox event")
    void shouldHandlePaymentSuccessSuccessfully() throws Exception {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-999",
                "SUCCESS",
                new BigDecimal("150.00"),
                null,
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        // Verify order transitioned to PAID
        assertEquals(OrderStatus.PAID, testOrder.getStatus());
        verify(orderRepository).save(testOrder);

        // Verify Outbox event created
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent capturedOutbox = outboxCaptor.getValue();
        assertEquals("OrderPaid", capturedOutbox.getEventType());
        assertEquals("Order", capturedOutbox.getAggregateType());
        assertEquals(testOrderId.toString(), capturedOutbox.getAggregateId());
        assertTrue(capturedOutbox.getPayload().contains("pay-999"));

        // Verify AMQP ACK recorded
        assertEquals(testDeliveryTag, recordedAckTag);
        assertFalse(recordedAckMultiple);
        assertNull(recordedNackTag);
    }

    @Test
    @DisplayName("Should handle payment FAILED, trigger SAGA stock release compensation, and cancel order")
    void shouldHandlePaymentFailureAndTriggerSagaCompensation() throws Exception {
        OrderItem item1 = new OrderItem("prod-1", "Mechanical Keyboard", new BigDecimal("100.00"), 2);
        OrderItem item2 = new OrderItem("prod-2", "Gaming Mouse", new BigDecimal("50.00"), 1);
        testOrder.addItem(item1);
        testOrder.addItem(item2);

        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-failed-888",
                "FAILED",
                new BigDecimal("250.00"),
                "Card declined due to insufficient funds",
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        // Verify SAGA compensation: stock released for all order items
        verify(catalogServiceClient).releaseStock(eq("prod-1"), eq(2), isNull());
        verify(catalogServiceClient).releaseStock(eq("prod-2"), eq(1), isNull());

        // Verify order transitioned to CANCELLED
        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
        verify(orderRepository).save(testOrder);

        // Verify Outbox event created
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent capturedOutbox = outboxCaptor.getValue();
        assertEquals("OrderCancelled", capturedOutbox.getEventType());
        assertTrue(capturedOutbox.getPayload().contains("Card declined due to insufficient funds"));

        // Verify AMQP ACK recorded
        assertEquals(testDeliveryTag, recordedAckTag);
        assertFalse(recordedAckMultiple);
        assertNull(recordedNackTag);
    }

    @Test
    @DisplayName("Should handle duplicate payment SUCCESS idempotently when order is already PAID")
    void shouldHandleIdempotentDuplicatePaymentSuccess() throws Exception {
        testOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-dup-123",
                "SUCCESS",
                new BigDecimal("100.00"),
                null,
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        assertEquals(testDeliveryTag, recordedAckTag);
    }

    @Test
    @DisplayName("Should handle duplicate payment FAILED idempotently when order is already CANCELLED")
    void shouldHandleIdempotentDuplicatePaymentFailure() throws Exception {
        testOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-dup-failed",
                "FAILED",
                new BigDecimal("100.00"),
                "Declined",
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        verify(catalogServiceClient, never()).releaseStock(any(), anyInt(), any());
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        assertEquals(testDeliveryTag, recordedAckTag);
    }

    @Test
    @DisplayName("Should reject message to DLQ (nack false false) when order is not found")
    void shouldRejectToDlqWhenOrderNotFound() throws IOException {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-missing",
                "SUCCESS",
                new BigDecimal("100.00"),
                null,
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        assertEquals(testDeliveryTag, recordedNackTag);
        assertFalse(recordedNackMultiple);
        assertFalse(recordedNackRequeue); // DLQ routed
        assertNull(recordedAckTag);
    }

    @Test
    @DisplayName("Should reject message to DLQ when event payload is null or orderId is missing")
    void shouldRejectToDlqWhenEventPayloadInvalid() throws IOException {
        paymentResultListener.handlePaymentResult(null, testDeliveryTag, proxyChannel);

        assertEquals(testDeliveryTag, recordedNackTag);
        assertFalse(recordedNackRequeue);

        recordedNackTag = null;
        PaymentResultEventDto eventWithoutOrderId = new PaymentResultEventDto(
                null, testUserId, "pay-1", "SUCCESS", BigDecimal.TEN, null, Instant.now()
        );
        paymentResultListener.handlePaymentResult(eventWithoutOrderId, testDeliveryTag, proxyChannel);

        assertEquals(testDeliveryTag, recordedNackTag);
        assertFalse(recordedNackRequeue);
    }

    @Test
    @DisplayName("Should reject message to DLQ when payment status is unrecognized")
    void shouldRejectToDlqWhenUnknownStatus() throws IOException {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-unknown",
                "INVALID_STATUS",
                new BigDecimal("100.00"),
                null,
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        assertEquals(testDeliveryTag, recordedNackTag);
        assertFalse(recordedNackRequeue);
    }

    @Test
    @DisplayName("Should requeue message (nack false true) when transient unexpected exception occurs")
    void shouldRequeueOnTransientException() throws Exception {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(testOrder));
        doThrow(new RuntimeException("Database connection timeout")).when(orderRepository).save(any());

        PaymentResultEventDto event = new PaymentResultEventDto(
                testOrderId,
                testUserId,
                "pay-transient",
                "SUCCESS",
                new BigDecimal("100.00"),
                null,
                Instant.now()
        );

        paymentResultListener.handlePaymentResult(event, testDeliveryTag, proxyChannel);

        assertEquals(testDeliveryTag, recordedNackTag);
        assertTrue(recordedNackRequeue); // Requeue for retry
        assertNull(recordedAckTag);
    }
}
