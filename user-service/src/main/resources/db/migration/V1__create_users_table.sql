-- Enable UUID generation support in PostgreSQL
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Main users table — one row per registered user
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Unique index on email so the database itself rejects duplicate signups
CREATE UNIQUE INDEX idx_users_email ON users(email);

-- Separate table for roles — one row per (user, role) pair.
-- This is the physical table that @ElementCollection creates behind the scenes.
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);