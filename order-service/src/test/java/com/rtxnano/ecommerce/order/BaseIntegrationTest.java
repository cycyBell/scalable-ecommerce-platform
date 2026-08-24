package com.rtxnano.ecommerce.order;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * ==============================================================================
 * BASE INTEGRATION TEST: BaseIntegrationTest
 * ==============================================================================
 * Shared abstract test harness configuring ephemeral Testcontainers for
 * PostgreSQL 16 and RabbitMQ 3.13 with dynamic Spring context property injection.
 */
public abstract class BaseIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER;
    private static final RabbitMQContainer RABBITMQ_CONTAINER;

    static {
        boolean dockerAvailable = isDockerAvailable();

        if (dockerAvailable) {
            POSTGRES_CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderdb_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");
            POSTGRES_CONTAINER.start();

            RABBITMQ_CONTAINER = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                    .withExchange("order.exchange", "topic");
            RABBITMQ_CONTAINER.start();
        } else {
            POSTGRES_CONTAINER = null;
            RABBITMQ_CONTAINER = null;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
            registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        }

        if (RABBITMQ_CONTAINER != null && RABBITMQ_CONTAINER.isRunning()) {
            registry.add("spring.rabbitmq.host", RABBITMQ_CONTAINER::getHost);
            registry.add("spring.rabbitmq.port", RABBITMQ_CONTAINER::getAmqpPort);
            registry.add("spring.rabbitmq.username", RABBITMQ_CONTAINER::getAdminUsername);
            registry.add("spring.rabbitmq.password", RABBITMQ_CONTAINER::getAdminPassword);
        }
    }
}
