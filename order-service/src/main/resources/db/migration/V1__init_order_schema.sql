-- ==============================================================================
-- FLYWAY MIGRATION: V1__init_order_schema.sql
-- Description: Initialize core schema for Order Microservice
-- Datastore: PostgreSQL 16
-- Tables: orders, order_items, outbox_events, idempotency_keys
-- ==============================================================================

-- 1. ORDERS TABLE
-- Represents permanent, immutable purchase orders created from shopping carts.
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    shipping_address TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_total_positive CHECK (total_amount >= 0)
);

-- 2. ORDER ITEMS TABLE
-- Line items capturing product snapshot, price at time of purchase, and quantity.
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_subtotal CHECK (subtotal >= 0)
);

-- 3. TRANSACTIONAL OUTBOX TABLE
-- Solves the dual-write problem by persisting domain events in the same local ACID
-- transaction as orders. An asynchronous poller reads PENDING events and publishes to RabbitMQ.
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- 4. DISTRIBUTED IDEMPOTENCY KEYS TABLE
-- Prevents duplicate order placement on client retries or network timeouts.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    "key" VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

-- ==============================================================================
-- PERFORMANCE & QUERY INDEXES
-- ==============================================================================

-- Index for querying user order history (GET /orders?userId=...)
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);

-- Index for administrative queries filtering by order lifecycle status
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- Index for retrieving order items by parent order ID
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- Index for high-throughput Outbox Poller querying pending events in FIFO order
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created ON outbox_events(status, created_at ASC);

-- Index for scheduled cleanup of expired idempotency keys
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
