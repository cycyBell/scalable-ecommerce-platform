package com.rtxnano.ecommerce.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Order Service Application Unit Tests")
class OrderServiceApplicationTests {

    @Test
    @DisplayName("Application class should be loadable")
    void applicationClassLoads() {
        OrderServiceApplication app = new OrderServiceApplication();
        assertNotNull(app, "OrderServiceApplication instance should not be null");
    }
}
