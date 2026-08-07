/**
 * ==============================================================================
 * UNIT TEST SUITE: Authentication Middleware (`tests/unit/authMiddleware.test.js`)
 * ==============================================================================
 * 
 * TESTS COVERED:
 * 1. Valid Bearer JWT: Verifies extraction of userId (`sub`), roles, and `cart:userId` cart key.
 * 2. Guest Shopper Fallback: Verifies extraction of `X-Guest-Id` header and `cart:guest:guestId` key.
 * 3. Invalid/Expired JWT: Verifies fallback to `X-Guest-Id` if valid, or throwing UnauthorizedError.
 * 4. Missing Auth Credentials: Verifies 401 Unauthorized exception when neither JWT nor Guest ID is supplied.
 * 5. requireAuth Guard: Ensures unauthenticated guest shoppers are rejected from protected routes.
 * 6. requireRole Guard: Ensures RBAC permission checks work properly.
 * ==============================================================================
 */

const jwt = require('jsonwebtoken');
const config = require('../../src/config/env');
const { authenticate, requireAuth, requireRole } = require('../../src/middleware/authMiddleware');
const { UnauthorizedError, ForbiddenError } = require('../../src/middleware/errorHandler');

describe('Auth Middleware Unit Tests', () => {

    let mockReq;
    let mockRes;
    let mockNext;

    beforeEach(() => {
        mockReq = {
            headers: {}
        };
        mockRes = {};
        mockNext = jest.fn();
    });

    test('should authenticate valid Bearer JWT and set user context & cartKey', () => {
        const validToken = jwt.sign(
            { sub: 'user@example.com', roles: ['CUSTOMER'] },
            config.jwt.secret,
            { expiresIn: '1h' }
        );

        mockReq.headers.authorization = `Bearer ${validToken}`;

        authenticate(mockReq, mockRes, mockNext);

        expect(mockNext).toHaveBeenCalledWith();
        expect(mockReq.isAuthenticated).toBe(true);
        expect(mockReq.user).toEqual({ userId: 'user@example.com', roles: ['CUSTOMER'] });
        expect(mockReq.cartKey).toBe('cart:user@example.com');
        expect(mockReq.guestId).toBeNull();
    });

    test('should authenticate guest shopper via X-Guest-Id header', () => {
        mockReq.headers['x-guest-id'] = 'guest-uuid-12345';

        authenticate(mockReq, mockRes, mockNext);

        expect(mockNext).toHaveBeenCalledWith();
        expect(mockReq.isAuthenticated).toBe(false);
        expect(mockReq.user).toBeNull();
        expect(mockReq.guestId).toBe('guest-uuid-12345');
        expect(mockReq.cartKey).toBe('cart:guest:guest-uuid-12345');
    });

    test('should fallback to X-Guest-Id if Bearer JWT token signature is invalid', () => {
        const invalidToken = jwt.sign(
            { sub: 'user@example.com' },
            'wrong_secret_key'
        );

        mockReq.headers.authorization = `Bearer ${invalidToken}`;
        mockReq.headers['x-guest-id'] = 'fallback-guest-999';

        authenticate(mockReq, mockRes, mockNext);

        expect(mockNext).toHaveBeenCalledWith();
        expect(mockReq.isAuthenticated).toBe(false);
        expect(mockReq.guestId).toBe('fallback-guest-999');
        expect(mockReq.cartKey).toBe('cart:guest:fallback-guest-999');
    });

    test('should throw UnauthorizedError if neither Bearer JWT nor X-Guest-Id is present', () => {
        authenticate(mockReq, mockRes, mockNext);

        expect(mockNext).toHaveBeenCalledWith(expect.any(UnauthorizedError));
        const error = mockNext.mock.calls[0][0];
        expect(error.statusCode).toBe(401);
        expect(error.errorCode).toBe('UNAUTHORIZED');
    });

    test('requireAuth should allow authenticated users and block guests', () => {
        mockReq.isAuthenticated = true;
        mockReq.user = { userId: 'user@example.com' };

        requireAuth(mockReq, mockRes, mockNext);
        expect(mockNext).toHaveBeenCalledWith();

        // Test guest rejection
        mockNext.mockClear();
        mockReq.isAuthenticated = false;
        mockReq.user = null;

        requireAuth(mockReq, mockRes, mockNext);
        expect(mockNext).toHaveBeenCalledWith(expect.any(UnauthorizedError));
    });

    test('requireRole should allow authorized roles and reject unauthorized roles', () => {
        const adminGuard = requireRole('ADMIN');

        mockReq.isAuthenticated = true;
        mockReq.user = { userId: 'admin@example.com', roles: ['ADMIN', 'CUSTOMER'] };

        adminGuard(mockReq, mockRes, mockNext);
        expect(mockNext).toHaveBeenCalledWith();

        // Test non-admin user
        mockNext.mockClear();
        mockReq.user = { userId: 'customer@example.com', roles: ['CUSTOMER'] };

        adminGuard(mockReq, mockRes, mockNext);
        expect(mockNext).toHaveBeenCalledWith(expect.any(ForbiddenError));
    });
});
