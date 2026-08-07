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
 * 3. Actuator Health Probe (`/health`): Performs real-time Redis ping check to verify system health.
 * 4. Microservice Routes: Mounts `/cart` and `/api/v1/cart` endpoints.
 * 5. Centralized Error Handling: `notFoundHandler` and `errorHandler` mount at the end of the pipeline.
 * 6. Graceful Process Termination: Listens to SIGTERM/SIGINT signals to close Redis connections cleanly.
 * ==============================================================================
 */

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

// Load and validate environment configuration (Fail-Fast Principle)
const config = require('./config/env');

// Import Redis Connection Manager and Health Probe
const { checkRedisHealth, closeRedisConnection } = require('./config/redis');

// Import Microservice Routes
const cartRoutes = require('./routes/cartRoutes');

// Import Global Error Handling Middlewares
const { notFoundHandler, errorHandler } = require('./middleware/errorHandler');

// Initialize the Express HTTP application instance
const app = express();

// ==============================================================================
// GLOBAL MIDDLEWARE PIPELINE
// ==============================================================================

// 1. Helmet Middleware: Attaches standard security headers to all HTTP responses.
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
 * Purpose: Provides a real-time liveness and readiness probe for Docker / Kubernetes.
 * Performs an active Redis PING check to confirm database connectivity.
 */
app.get('/health', async (req, res) => {
    const redisHealth = await checkRedisHealth();
    const isHealthy = redisHealth.status === 'UP';

    const statusCode = isHealthy ? 200 : 503;

    res.status(statusCode).json({
        status: isHealthy ? 'UP' : 'DOWN',
        service: 'shopping-cart-service',
        timestamp: new Date().toISOString(),
        environment: config.nodeEnv,
        components: {
            redis: redisHealth
        }
    });
});

// ==============================================================================
// APPLICATION ROUTES
// ==============================================================================
// Mount Shopping Cart Routes under both /cart and /api/v1/cart
app.use('/cart', cartRoutes);
app.use('/api/v1/cart', cartRoutes);

// ==============================================================================
// ERROR HANDLING MIDDLEWARE PIPELINE (MUST BE AT THE END OF ALL ROUTES)
// ==============================================================================

// 1. 404 Handler: Catches requests for non-existent HTTP routes
app.use(notFoundHandler);

// 2. Global Error Handler: Formats all thrown errors into clean JSON responses
app.use(errorHandler);

// ==============================================================================
// SERVER STARTUP & PROCESS MANAGEMENT
// ==============================================================================
const PORT = config.port;
let server = null;

// Start the HTTP server listener only when not imported by Supertest during testing
if (config.nodeEnv !== 'test') {
    server = app.listen(PORT, () => {
        console.log(`=============================================================`);
        console.log(`🚀 SHOPPING CART MICROSERVICE STARTED SUCCESSFULLY`);
        console.log(`=============================================================`);
        console.log(`   Port:         ${PORT}`);
        console.log(`   Environment:  ${config.nodeEnv}`);
        console.log(`   Catalog URL:  ${config.services.catalogUrl}`);
        console.log(`   Healthcheck:  http://localhost:${PORT}/health`);
        console.log(`=============================================================`);
    });
}

// ==============================================================================
// GRACEFUL SHUTDOWN HANDLERS
// ==============================================================================
/**
 * Graceful Shutdown Function
 * Closes the HTTP server first to stop accepting new requests, then safely closes
 * active Redis connections before exiting the Node.js process.
 */
async function handleGracefulShutdown(signal) {
    console.log(`\n[Process Signal] ${signal} received. Initiating graceful shutdown...`);
    
    if (server) {
        server.close(async () => {
            console.log('[HTTP Server] Stopped listening for new HTTP requests.');
            await closeRedisConnection();
            console.log('[Shutdown Complete] Exiting Node process cleanly.');
            process.exit(0);
        });
    } else {
        await closeRedisConnection();
        process.exit(0);
    }

    // Force exit after 10 seconds if shutdown hangs
    setTimeout(() => {
        console.error('[Shutdown Error] Could not close connections in time, forcing exit.');
        process.exit(1);
    }, 10000);
}

// Listen to SIGINT (Ctrl+C in terminal) and SIGTERM (Container stop signal)
process.on('SIGINT', () => handleGracefulShutdown('SIGINT'));
process.on('SIGTERM', () => handleGracefulShutdown('SIGTERM'));

// Export app for integration testing with Supertest
module.exports = app;
