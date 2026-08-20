package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.config.RabbitMqConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("RabbitMqConfig Topology Unit Tests")
class RabbitMqConfigTests {

    @Test
    @DisplayName("Should initialize AMQP Exchanges, Queues, DLQ Bindings and Converters correctly")
    void shouldInitializeAmqpTopologyCorrectly() {
        RabbitMqConfig config = new RabbitMqConfig();
        ReflectionTestUtils.setField(config, "orderExchangeName", "order.exchange");
        ReflectionTestUtils.setField(config, "orderDlxExchangeName", "order.dlx.exchange");
        ReflectionTestUtils.setField(config, "paymentResultQueueName", "order.payment-result.queue");
        ReflectionTestUtils.setField(config, "paymentResultDlqName", "order.payment-result.dlq");
        ReflectionTestUtils.setField(config, "orderPaidRoutingKey", "order.paid");
        ReflectionTestUtils.setField(config, "orderCancelledRoutingKey", "order.cancelled");

        // 1. Topic Exchange
        TopicExchange orderExchange = config.orderExchange();
        assertNotNull(orderExchange);
        assertEquals("order.exchange", orderExchange.getName());
        assertTrue(orderExchange.isDurable());

        // 2. Dead-Letter Exchange
        DirectExchange dlxExchange = config.orderDlxExchange();
        assertNotNull(dlxExchange);
        assertEquals("order.dlx.exchange", dlxExchange.getName());
        assertTrue(dlxExchange.isDurable());

        // 3. Payment Result Queue with DLX args
        Queue paymentQueue = config.paymentResultQueue();
        assertNotNull(paymentQueue);
        assertEquals("order.payment-result.queue", paymentQueue.getName());
        assertEquals("order.dlx.exchange", paymentQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("order.payment-result.queue.dlq", paymentQueue.getArguments().get("x-dead-letter-routing-key"));

        // 4. DLQ & Binding
        Queue dlq = config.paymentResultDlq();
        assertNotNull(dlq);
        assertEquals("order.payment-result.dlq", dlq.getName());

        Binding dlqBinding = config.paymentResultDlqBinding();
        assertNotNull(dlqBinding);
        assertEquals("order.payment-result.dlq", dlqBinding.getDestination());
        assertEquals("order.dlx.exchange", dlqBinding.getExchange());
        assertEquals("order.payment-result.queue.dlq", dlqBinding.getRoutingKey());

        // 5. Message Converter
        MessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
        assertTrue(converter instanceof Jackson2JsonMessageConverter);

        // 6. RabbitTemplate
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);
        assertNotNull(template);
        assertNotNull(template.getMessageConverter());
        assertTrue(template.getMessageConverter() instanceof Jackson2JsonMessageConverter);
    }
}
