/**
 * ==============================================================================
 * MODULE: Environment Variables Configuration & Validation Loader
 * ==============================================================================
 * 
 * WHY IS THIS MODULE IMPORTANT?
 * In a professional microservices architecture, services should fail fast during
 * startup if critical configuration settings (like database hosts or secret keys)
 * are missing, rather than crashing silently at runtime during user requests.
 * 
 * WHAT DOES THIS FILE DO?
 * 1. Loads configuration values from the local `.env` file into `process.env`.
 * 2. Validates that every mandatory configuration property is present.
 * 3. Provides clean default fallbacks for non-critical settings (e.g. PORT 8002).
 * 4. Exports a single, frozen configuration object so no other part of the app
 *    can accidentally modify configuration parameters at runtime.
 * ==============================================================================
 */

const dotenv = require('dotenv');
const path = require('path');

// Step 1: Load environment variables from the .env file in the service root directory.
// dotenv.config() parses the file and assigns key=value pairs directly to Node's `process.env`.
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

// List of mandatory environment variable keys required for the service to function properly.
const REQUIRED_ENV_VARS = [
    'JWT_SECRET',
    'PRODUCT_CATALOG_URL'
];

/**
 * Validates that all required environment variables are set.
 * Throws a descriptive Error if any critical variable is missing.
 */
function validateEnv() {
    const missing = REQUIRED_ENV_VARS.filter(key => !process.env[key]);

    if (missing.length > 0) {
        throw new Error(
            `[FATAL CONFIG ERROR] Missing required environment variables: ${missing.join(', ')}. ` +
            `Please check your .env file or deployment settings.`
        );
    }
}

// Perform validation immediately upon module import (Fail-Fast Principle).
validateEnv();

/**
 * Frozen Configuration Object.
 * Object.freeze prevents accidental runtime mutation (e.g. config.port = 9000).
 */
const config = Object.freeze({
    // Server Port: Defaults to 8002 if PORT is not set in environment.
    port: parseInt(process.env.PORT || '8002', 10),

    // Node Environment: 'development', 'production', or 'test'.
    nodeEnv: process.env.NODE_ENV || 'development',

    // Redis Datastore Configuration
    redis: {
        host: process.env.REDIS_HOST || 'localhost',
        port: parseInt(process.env.REDIS_PORT || '6381', 10),
        password: process.env.REDIS_PASSWORD || undefined,
    },

    // JWT Configuration: Used to statelessly verify access tokens issued by user-service.
    jwt: {
        secret: process.env.JWT_SECRET,
    },

    // Downstream Microservice URLs
    services: {
        catalogUrl: process.env.PRODUCT_CATALOG_URL || 'http://product-catalog-service:8000',
    }
});

module.exports = config;
