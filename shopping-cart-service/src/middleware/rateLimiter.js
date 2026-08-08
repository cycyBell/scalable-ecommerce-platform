/**
 * ==============================================================================
 * MODULE: Rate Limiting Middleware (`src/middleware/rateLimiter.js`)
 * ==============================================================================
 * 
 * WHY IS RATE LIMITING ESSENTIAL?
 * 1. Denial of Service (DoS) Prevention: Prevents automated botnets or malicious users
 *    from overwhelming Redis connection pools or flooding the downstream Catalog Service.
 * 2. Resource Protection: Carts perform HTTP lookups and Redis reads/writes; capping
 *    request frequency preserves event-loop performance.
 * 3. Security Hardening: Defends against brute-force cart manipulation and inventory
 *    scraping attempts.
 * ==============================================================================
 */

const { rateLimit, ipKeyGenerator } = require('express-rate-limit');

/**
 * Standard Shopping Cart Rate Limiting Middleware.
 * Window: 15 minutes.
 * Maximum: 100 requests per window per IP/User.
 * Returns: HTTP 429 Too Many Requests with standardized JSON error response.
 */
const cartRateLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15-minute sliding window
    max: 100, // Limit each IP or User to 100 requests per 15-minute window
    standardHeaders: true, // Return rate limit info in standard `RateLimit-*` headers
    legacyHeaders: false, // Disable the `X-RateLimit-*` headers
    
    // Key generator: throttles per authenticated user if available, otherwise per client IP
    keyGenerator: (req) => {
        if (req.user?.userId) {
            return `user:${req.user.userId}`;
        }
        if (req.headers['x-guest-id']) {
            return `guest:${req.headers['x-guest-id']}`;
        }
        return ipKeyGenerator(req.ip);
    },

    // Custom handler returning the standard microservice JSON error structure
    handler: (req, res) => {
        res.status(429).json({
            error: {
                code: 'RATE_LIMIT_EXCEEDED',
                message: 'Too many shopping cart requests from this client. Please slow down and try again later.',
                statusCode: 429,
                timestamp: new Date().toISOString(),
                path: req.originalUrl || req.url
            }
        });
    }
});

module.exports = {
    cartRateLimiter
};
