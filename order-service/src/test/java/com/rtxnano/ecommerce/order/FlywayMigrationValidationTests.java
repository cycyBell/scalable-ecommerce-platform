package com.rtxnano.ecommerce.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Flyway Schema Migration Script Validation Tests")
class FlywayMigrationValidationTests {

    private static final String MIGRATION_PATH = "db/migration/V1__init_order_schema.sql";

    @Test
    @DisplayName("V1 migration script should exist on the classpath and not be empty")
    void v1MigrationScriptShouldExist() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        assertTrue(resource.exists(), "V1__init_order_schema.sql must exist on the classpath");

        String content;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            content = reader.lines().collect(Collectors.joining("\n"));
        }

        assertFalse(content.isBlank(), "Migration script must not be blank");
        assertTrue(content.contains("CREATE TABLE IF NOT EXISTS orders"), "Must define orders table");
        assertTrue(content.contains("CREATE TABLE IF NOT EXISTS order_items"), "Must define order_items table");
        assertTrue(content.contains("CREATE TABLE IF NOT EXISTS outbox_events"), "Must define outbox_events table");
        assertTrue(content.contains("CREATE TABLE IF NOT EXISTS idempotency_keys"), "Must define idempotency_keys table");
    }

    @Test
    @DisplayName("Migration script should define all required indexes and foreign keys")
    void v1MigrationScriptShouldDefineIndexesAndConstraints() throws Exception {
        ClassPathResource resource = new ClassPathResource(MIGRATION_PATH);
        String content;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            content = reader.lines().collect(Collectors.joining("\n"));
        }

        // Indexes
        assertTrue(content.contains("idx_orders_user_id"), "Must create index on orders(user_id)");
        assertTrue(content.contains("idx_orders_status"), "Must create index on orders(status)");
        assertTrue(content.contains("idx_order_items_order_id"), "Must create index on order_items(order_id)");
        assertTrue(content.contains("idx_outbox_events_status_created"), "Must create index on outbox_events(status, created_at)");
        assertTrue(content.contains("idx_idempotency_keys_expires_at"), "Must create index on idempotency_keys(expires_at)");

        // Foreign keys & constraints
        assertTrue(content.contains("REFERENCES orders(id) ON DELETE CASCADE"), "order_items must cascade delete with parent order");
        assertTrue(content.contains("chk_orders_status"), "orders must enforce valid lifecycle status check");
        assertTrue(content.contains("chk_outbox_status"), "outbox_events must enforce status check");
        assertTrue(content.contains("chk_idempotency_status"), "idempotency_keys must enforce status check");
    }
}
