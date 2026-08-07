/**
 * ==============================================================================
 * MODULE: Zero-DB Stateless JWT & Guest Authentication Middleware (`src/middleware/authMiddleware.js`)
 * ==============================================================================
 * 
 * ARCHITECTURAL CONCEPTS DEMONSTRATED IN THIS FILE:
 * 1. Zero-DB Stateless JWT Verification:
 *    Because the Java User Service signs access tokens with the shared `JWT_SECRET`,
 *    this Node.js Shopping Cart Service can verify token authenticity and extract user
 *    claims (`sub`, `roles`) entirely in-memory without making network RPC calls back
 *    to User Service or hitting PostgreSQL!
 * 
 * 2. Dual Identity Resolution (Authenticated vs Guest Shoppers):
 *    Shopping carts must support both logged-in customers (`cart:{userId}`) and anonymous
 *    guest visitors (`cart:guest:{guestId}`).
 * 
 * 3. Unified Cart Key Resolution (`req.cartKey`):
 *    The middleware computes a single standardized Redis key (`req.cartKey`) attached to
 *    the Express request object:
 *    - Authenticated User: `cart:user@example.com`
 *    - Guest Shopper:      `cart:guest:guest-uuid-12345`
 * ==============================================================================
 */

const jwt = require('jsonwebtoken');
const config = require('../config/env');
const { UnauthorizedError, ForbiddenError } = require('./errorHandler');

/**
 * Authentication Middleware: Resolves request identity from Bearer JWT or Guest ID.
 * 
 * Resolution Logic Flow:
 * 1. Inspect `Authorization` HTTP header for `Bearer <token>`.
 * 2. If present, verify token signature using shared `JWT_SECRET`.
 *    - On Success: Set `req.user = { userId: payload.sub, roles: payload.roles }`,
 *      `req.cartKey = 'cart:' + payload.sub`, `req.isAuthenticated = true`.
 * 3. If no JWT or token is invalid, inspect `X-Guest-Id` header (e.g. `x-guest-id: guest_abc123`).
 *    - On Success: Set `req.guestId = guestId`, `req.cartKey = 'cart:guest:' + guestId`,
 *      `req.user = null`, `req.isAuthenticated = false`.
 * 4. If neither JWT nor Guest ID is provided, throw `UnauthorizedError` (HTTP 401).
 */
function authenticate(req, res, next) {
    try {
        const authHeader = req.headers.authorization;
        const guestIdHeader = req.headers['x-guest-id'];

        // Option 1: Attempt Bearer JWT Token Verification
        if (authHeader && authHeader.startsWith('Bearer ')) {
            const token = authHeader.substring(7).trim();

            if (token) {
                try {
                    // Verify JWT signature & expiration using shared HMAC secret
                    const payload = jwt.verify(token, config.jwt.secret);

                    // Extract user identity claims (sub = email/userId, roles = ['CUSTOMER'])
                    const userId = payload.sub;
                    const roles = payload.roles || [];

                    if (!userId) {
                        throw new UnauthorizedError('Malformed JWT payload: missing subject (sub) claim.');
                    }

                    // Attach authenticated user context to Express request
                    req.user = { userId, roles };
                    req.cartKey = `cart:${userId}`;
                    req.isAuthenticated = true;
                    req.guestId = null;

                    return next();
                } catch (jwtErr) {
                    // If JWT token is present but invalid/expired, fall back to guest ID if available
                    console.warn(`[Auth Warning] JWT verification failed (${jwtErr.message}). Checking guest fallback...`);
                }
            }
        }

        // Option 2: Fallback to Anonymous Guest Shopper ID (`X-Guest-Id` header)
        if (guestIdHeader && typeof guestIdHeader === 'string' && guestIdHeader.trim().length > 0) {
            const cleanGuestId = guestIdHeader.trim();

            req.user = null;
            req.guestId = cleanGuestId;
            req.cartKey = `cart:guest:${cleanGuestId}`;
            req.isAuthenticated = false;

            return next();
        }

        // Option 3: Neither valid JWT nor Guest ID supplied
        throw new UnauthorizedError(
            'Authentication required. Please provide a valid Bearer token in the Authorization header ' +
            'or a guest identifier in the X-Guest-Id header.'
        );

    } catch (err) {
        next(err);
    }
}

/**
 * Strict Authentication Enforcement Guard Middleware.
 * Use on endpoints that require a logged-in user session (e.g. `POST /cart/merge`).
 * Rejects anonymous guest requests with HTTP 401 Unauthorized.
 */
function requireAuth(req, res, next) {
    if (!req.isAuthenticated || !req.user) {
        return next(new UnauthorizedError('This action requires an authenticated user account. Please log in.'));
    }
    next();
}

/**
 * Role-Based Access Control (RBAC) Guard Middleware.
 * Restricts access to specific user roles (e.g. `requireRole('ADMIN')`).
 * 
 * @param {string|string[]} requiredRoles Single role or list of acceptable roles.
 */
function requireRole(requiredRoles) {
    const rolesArray = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];

    return (req, res, next) => {
        if (!req.isAuthenticated || !req.user) {
            return next(new UnauthorizedError('Authentication required.'));
        }

        const userRoles = req.user.roles || [];
        const hasPermission = rolesArray.some(role => userRoles.includes(role));

        if (!hasPermission) {
            return next(new ForbiddenError(`Access denied. Required role(s): [${rolesArray.join(', ')}].`));
        }

        next();
    };
}

module.exports = {
    authenticate,
    requireAuth,
    requireRole
};
