package com.rtxnano.ecommerce.order.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.event.OrderCancelledEvent;
import com.rtxnano.ecommerce.order.domain.event.OrderPaidEvent;
import com.rtxnano.ecommerce.order.domain.statemachine.OrderStateMachine;
import com.rtxnano.ecommerce.order.dto.PaymentResultEventDto;
import com.rtxnano.ecommerce.order.repository.OrderRepository;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * ==============================================================================
 * CONSUMER: PaymentResultListener
 * ==============================================================================
 * Listens for asynchronous payment outcomes from the Payment Microservice on
 * 'order.payment-result.queue'. Implements SAGA compensation (inventory release)
 * on payment failure and advances order state to PAID on payment success.
 */
@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderRepository orderRepository;
    private final CatalogServiceClient catalogServiceClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentResultListener(OrderRepository orderRepository,
                                 CatalogServiceClient catalogServiceClient,
                                 OutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.catalogServiceClient = catalogServiceClient;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes and processes payment result events with manual AMQP acknowledgment.
     */
    @RabbitListener(queues = "${app.rabbitmq.queues.payment-result:order.payment-result.queue}")
    @Transactional
    public void handlePaymentResult(PaymentResultEventDto event,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                    Channel channel) throws IOException {
        if (event == null || event.orderId() == null) {
            log.error("Received null or unidentifiable payment result payload; rejecting to DLQ");
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        log.info("Received PaymentResult event for orderId '{}' [status={}, paymentId='{}']",
                event.orderId(), event.status(), event.paymentId());

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.error("Order '{}' not found for payment result; rejecting to DLQ", event.orderId());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        Order order = orderOpt.get();

        try {
            if (event.isSuccess()) {
                handlePaymentSuccess(order, event);
            } else if (event.isFailed()) {
                handlePaymentFailure(order, event);
            } else {
                log.warn("Unknown payment result status '{}' for order '{}'; rejecting to DLQ",
                        event.status(), order.getId());
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // Successfully processed; acknowledge message
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Error processing payment result for order '{}': {}", order.getId(), ex.getMessage(), ex);
            // Requeue if temporary system/database error
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * Handles payment success: updates state to PAID and generates OrderPaidEvent in outbox.
     */
    private void handlePaymentSuccess(Order order, PaymentResultEventDto event) throws Exception {
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order '{}' is already in PAID state (idempotent ignore)", order.getId());
            return;
        }

        OrderStateMachine.validateTransition(order.getStatus(), OrderStatus.PAID);
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        // Generate outbox event
        Instant paidTimestamp = event.timestamp() != null ? event.timestamp() : Instant.now();
        OrderPaidEvent paidEvent = new OrderPaidEvent(
                order.getId(),
                order.getUserId(),
                event.paymentId(),
                paidTimestamp
        );

        String payloadJson = objectMapper.writeValueAsString(paidEvent);
        OutboxEvent outboxEvent = new OutboxEvent(
                "Order",
                order.getId().toString(),
                "OrderPaid",
                payloadJson
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Order '{}' successfully transitioned to PAID and OrderPaidEvent persisted to outbox", order.getId());
    }

    /**
     * Handles payment failure: triggers SAGA compensation (stock release), updates state to CANCELLED,
     * and generates OrderCancelledEvent in outbox.
     */
    private void handlePaymentFailure(Order order, PaymentResultEventDto event) throws Exception {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order '{}' is already in CANCELLED state (idempotent ignore)", order.getId());
            return;
        }

        OrderStateMachine.validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        // SAGA Compensating Action: Release reserved inventory back to catalog service
        log.info("Triggering SAGA compensating stock release for cancelled order '{}'", order.getId());
        for (OrderItem item : order.getItems()) {
            try {
                catalogServiceClient.releaseStock(item.getProductId(), item.getQuantity(), null);
                log.debug("Released {} unit(s) of product '{}' for order '{}'",
                        item.getQuantity(), item.getProductId(), order.getId());
            } catch (Exception ex) {
                log.error("Failed to release stock for product '{}' (order '{}'): {}",
                        item.getProductId(), order.getId(), ex.getMessage());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Generate outbox event
        String reason = "Payment failed: " + (event.failureReason() != null ? event.failureReason() : "Transaction declined");
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                order.getId(),
                order.getUserId(),
                reason,
                Instant.now()
        );

        String payloadJson = objectMapper.writeValueAsString(cancelledEvent);
        OutboxEvent outboxEvent = new OutboxEvent(
                "Order",
                order.getId().toString(),
                "OrderCancelled",
                payloadJson
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Order '{}' transitioned to CANCELLED and OrderCancelledEvent persisted to outbox", order.getId());
    }
}
