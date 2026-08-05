/**
 * ==============================================================================
 * APPLICATION ENTRY POINT: Shopping Cart Microservice
 * ==============================================================================
 * 
 * WELCOME TO THE SHOPPING CART SERVICE!
 * This microservice is responsible for managing short-lived, volatile shopping carts
 * for both guest visitors and authenticated users.
 * 
 * CORE ARCHITECTURAL CONCEPTS DEMONSTRATED IN THIS FILE:
 * 1. Express Framework Setup: Minimalist, fast HTTP server routing.
 * 2. Security Best Practices:
 *    - `helmet()`: Sets modern HTTP response headers to defend against XSS, clickjacking, etc.
 *    - `cors()`: Controls cross-origin HTTP requests from web frontends.
 *    - `express.json()`: Parses incoming JSON bodies securely up to 10MB limits.
 * 3. Actuator Health Probe (`/health`): Used by Docker and Kubernetes to monitor service liveness.
 * 4. Graceful Startup: Validates environment settings before listening on network ports.
 * ==============================================================================
 */

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

// Load and validate environment configuration (Fail-Fast Principle)
const config = require('./config/env');

// Initialize the Express HTTP application instance
const app = express();

// ==============================================================================
// GLOBAL MIDDLEWARE PIPELINE
// ==============================================================================

// 1. Helmet Middleware: Attaches standard security headers to all HTTP responses.
// Learn more: https://helmetjs.github.io/
app.use(helmet());

// 2. CORS Middleware: Enables Cross-Origin Resource Sharing for web client applications.
app.use(cors());

// 3. Body Parser Middleware: Parses incoming requests with JSON payloads into `req.body`.
app.use(express.json({ limit: '10mb' }));

// ==============================================================================
// HEALTHCHECK & MONITORING ROUTE
// ==============================================================================
/**
 * GET /health
 * Purpose: Provides a lightweight liveness probe endpoint for Kubernetes / Docker / Actuator.
 * Returns: HTTP 200 OK with JSON status payload.
 */
app.get('/health', (req, res) => {
    res.status(200).json({
        status: 'UP',
        service: 'shopping-cart-service',
        timestamp: new Date().toISOString(),
        environment: config.nodeEnv
    });
});

// ==============================================================================
// SERVER STARTUP & PROCESS MANAGEMENT
// ==============================================================================
const PORT = config.port;

// Start the HTTP server listener
const server = app.listen(PORT, () => {
    console.log(`=============================================================`);
    console.log(`🚀 SHOPPING CART MICROSERVICE STARTED SUCCESSFULLY`);
    console.log(`=============================================================`);
    console.log(`   Port:         ${PORT}`);
    console.log(`   Environment:  ${config.nodeEnv}`);
    console.log(`   Catalog URL:  ${config.services.catalogUrl}`);
    console.log(`   Healthcheck:  http://localhost:${PORT}/health`);
    console.log(`=============================================================`);
});

// Export app for integration testing with Supertest
module.exports = app;
