package com.rtxnano.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ==============================================================================
 * APPLICATION ENTRYPOINT: Order Microservice
 * ==============================================================================
 * 
 * CORE RESPONSIBILITIES:
 * 1. Converts shopping cart sessions into permanent, immutable purchase orders.
 * 2. Implements the Transactional Outbox Pattern to guarantee at-least-once
 *    event publishing to RabbitMQ without distributed dual-write inconsistency.
 * 3. Enforces distributed API idempotency to prevent duplicate orders.
 * 4. Executes strict order state machine transitions (PENDING -> PAID -> SHIPPED -> DELIVERED).
 * 5. Consumes asynchronous payment outcomes (SAGA orchestration/choreography).
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
