package com.rtxnano.ecommerce.order.outbox;

import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OutboxStatus;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ==============================================================================
 * WORKER: OutboxEventPublisher
 * ==============================================================================
 * Scheduled background daemon that polls PENDING events from the transactional
 * outbox database table (FIFO) and publishes them to the RabbitMQ Topic Exchange.
 * Provides at-least-once delivery guarantees and dead-letter retry tracking.
 */
@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.order:order.exchange}")
    private String orderExchange;

    @Value("${app.outbox.poller.batch-size:50}")
    private int batchSize = 50;

    @Value("${app.outbox.poller.max-retry-attempts:5}")
    private int maxRetryAttempts = 5;

    @Value("${app.rabbitmq.routing-keys.order-created:order.created}")
    private String orderCreatedRoutingKey = "order.created";

    @Value("${app.rabbitmq.routing-keys.order-paid:order.paid}")
    private String orderPaidRoutingKey = "order.paid";

    @Value("${app.rabbitmq.routing-keys.order-cancelled:order.cancelled}")
    private String orderCancelledRoutingKey = "order.cancelled";

    @Value("${app.rabbitmq.routing-keys.order-shipped:order.shipped}")
    private String orderShippedRoutingKey = "order.shipped";

    @Value("${app.rabbitmq.routing-keys.order-delivered:order.delivered}")
    private String orderDeliveredRoutingKey = "order.delivered";

    public OutboxEventPublisher(OutboxEventRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Periodically polls pending outbox events and pushes them to RabbitMQ.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poller.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    /**
     * Publishes a single outbox event to RabbitMQ and manages status transitions.
     */
    protected void publishSingleEvent(OutboxEvent event) {
        String routingKey = resolveRoutingKey(event.getEventType());

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.getId().toString())
                    .setHeader("eventType", event.getEventType())
                    .setHeader("aggregateType", event.getAggregateType())
                    .setHeader("aggregateId", event.getAggregateId())
                    .build();

            rabbitTemplate.send(orderExchange, routingKey, message);

            event.markAsPublished();
            outboxRepository.save(event);

            log.info("Successfully published outbox event [id={}, type='{}', aggregateId='{}', routingKey='{}']",
                    event.getId(), event.getEventType(), event.getAggregateId(), routingKey);
        } catch (Exception ex) {
            log.error("Failed to publish outbox event [id={}, type='{}', routingKey='{}']: {}",
                    event.getId(), event.getEventType(), routingKey, ex.getMessage());

            event.incrementRetryCount();
            if (event.getRetryCount() >= maxRetryAttempts) {
                event.markAsFailed();
                log.error("Outbox event [id={}, type='{}'] exceeded max retries ({}) and marked as FAILED",
                        event.getId(), event.getEventType(), maxRetryAttempts);
            }
            outboxRepository.save(event);
        }
    }

    /**
     * Resolves AMQP routing key based on domain event type.
     */
    public String resolveRoutingKey(String eventType) {
        if (eventType == null) {
            return "order.unknown";
        }
        return switch (eventType) {
            case "OrderCreated" -> orderCreatedRoutingKey;
            case "OrderPaid" -> orderPaidRoutingKey;
            case "OrderCancelled" -> orderCancelledRoutingKey;
            case "OrderShipped" -> orderShippedRoutingKey;
            case "OrderDelivered" -> orderDeliveredRoutingKey;
            default -> "order." + eventType.toLowerCase();
        };
    }
}
