package com.rtxnano.ecommerce.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ==============================================================================
 * CONFIGURATION: RabbitMQ AMQP Broker Topology
 * ==============================================================================
 * Configures Topic Exchanges, Dead-Letter Exchanges (DLX), Queues, and Jackson JSON
 * serialization for asynchronous inter-service event communication.
 */
@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    @Value("${app.rabbitmq.exchange.order:order.exchange}")
    private String orderExchangeName;

    @Value("${app.rabbitmq.exchange.dlx:order.dlx.exchange}")
    private String orderDlxExchangeName;

    @Value("${app.rabbitmq.queues.payment-result:order.payment-result.queue}")
    private String paymentResultQueueName;

    @Value("${app.rabbitmq.queues.payment-result-dlq:order.payment-result.dlq}")
    private String paymentResultDlqName;

    @Value("${app.rabbitmq.routing-keys.order-paid:order.paid}")
    private String orderPaidRoutingKey;

    @Value("${app.rabbitmq.routing-keys.order-cancelled:order.cancelled}")
    private String orderCancelledRoutingKey;

    /**
     * Primary Topic Exchange for Order domain events (order.created, order.cancelled, etc.).
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(orderExchangeName, true, false);
    }

    /**
     * Dead-Letter Exchange (DLX) for routing undeliverable or poisoned messages.
     */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(orderDlxExchangeName, true, false);
    }

    /**
     * Dead-Letter Queue (DLQ) for payment result failures.
     */
    @Bean
    public Queue paymentResultDlq() {
        return QueueBuilder.durable(paymentResultDlqName).build();
    }

    /**
     * Binds Payment Result DLQ to Dead Letter Exchange.
     */
    @Bean
    public Binding paymentResultDlqBinding() {
        return BindingBuilder.bind(paymentResultDlq())
                .to(orderDlxExchange())
                .with(paymentResultQueueName + ".dlq");
    }

    /**
     * Payment Result Queue with Dead-Letter Exchange redirection on rejection/TTL expiry.
     */
    @Bean
    public Queue paymentResultQueue() {
        return QueueBuilder.durable(paymentResultQueueName)
                .withArgument("x-dead-letter-exchange", orderDlxExchangeName)
                .withArgument("x-dead-letter-routing-key", paymentResultQueueName + ".dlq")
                .build();
    }

    /**
     * JSON Message Converter for serializing Java objects to/from standard JSON AMQP payloads.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate configured with publisher confirms and returns for guaranteed delivery.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true);

        // Publisher Confirms: Log acknowledgment from broker
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("RabbitMQ broker confirmed message receipt [correlationId={}]",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("RabbitMQ broker NACK'd message [correlationId={}, cause={}]",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });

        // Publisher Returns: Log returned unroutable messages
        template.setReturnsCallback(returned -> {
            log.error("RabbitMQ message returned unroutable: replyCode={}, replyText={}, exchange={}, routingKey={}",
                    returned.getReplyCode(), returned.getReplyText(), returned.getExchange(), returned.getRoutingKey());
        });

        return template;
    }
}
