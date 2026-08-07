/**
 * ==============================================================================
 * UNIT TEST SUITE: Centralized Error Handler & Custom Errors (`tests/unit/errorHandler.test.js`)
 * ==============================================================================
 */

const {
    ApiError,
    BadRequestError,
    UnauthorizedError,
    NotFoundError,
    notFoundHandler,
    errorHandler
} = require('../../src/middleware/errorHandler');

describe('Error Handler Unit Tests', () => {

    let mockReq;
    let mockRes;
    let mockNext;
    let consoleSpy;

    beforeAll(() => {
        consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterAll(() => {
        consoleSpy.mockRestore();
    });

    beforeEach(() => {
        mockReq = {
            method: 'GET',
            originalUrl: '/api/v1/test-endpoint'
        };
        mockRes = {
            status: jest.fn().mockReturnThis(),
            json: jest.fn()
        };
        mockNext = jest.fn();
    });

    test('notFoundHandler should pass a NotFoundError to next()', () => {
        notFoundHandler(mockReq, mockRes, mockNext);

        expect(mockNext).toHaveBeenCalledWith(expect.any(NotFoundError));
        const error = mockNext.mock.calls[0][0];
        expect(error.statusCode).toBe(404);
        expect(error.errorCode).toBe('NOT_FOUND');
    });

    test('errorHandler should format custom ApiError into standardized JSON response', () => {
        const error = new BadRequestError('Quantity must be greater than zero.');

        errorHandler(error, mockReq, mockRes, mockNext);

        expect(mockRes.status).toHaveBeenCalledWith(400);
        expect(mockRes.json).toHaveBeenCalledWith({
            error: {
                code: 'BAD_REQUEST',
                message: 'Quantity must be greater than zero.',
                statusCode: 400,
                timestamp: expect.any(String),
                path: '/api/v1/test-endpoint'
            }
        });
    });

    test('errorHandler should format JWT signature error into 401 Unauthorized', () => {
        const jwtError = new Error('invalid signature');
        jwtError.name = 'JsonWebTokenError';

        errorHandler(jwtError, mockReq, mockRes, mockNext);

        expect(mockRes.status).toHaveBeenCalledWith(401);
        expect(mockRes.json).toHaveBeenCalledWith({
            error: {
                code: 'UNAUTHORIZED',
                message: 'Invalid authentication token signature.',
                statusCode: 401,
                timestamp: expect.any(String),
                path: '/api/v1/test-endpoint'
            }
        });
    });

    test('errorHandler should format JWT expiration error into 401 Unauthorized', () => {
        const jwtError = new Error('jwt expired');
        jwtError.name = 'TokenExpiredError';

        errorHandler(jwtError, mockReq, mockRes, mockNext);

        expect(mockRes.status).toHaveBeenCalledWith(401);
        expect(mockRes.json).toHaveBeenCalledWith({
            error: {
                code: 'UNAUTHORIZED',
                message: 'Authentication token has expired. Please refresh your session.',
                statusCode: 401,
                timestamp: expect.any(String),
                path: '/api/v1/test-endpoint'
            }
        });
    });

    test('errorHandler should handle unexpected internal errors as HTTP 500', () => {
        const unexpectedError = new Error('Database connection failed unexpectedly.');

        errorHandler(unexpectedError, mockReq, mockRes, mockNext);

        expect(mockRes.status).toHaveBeenCalledWith(500);
        expect(mockRes.json).toHaveBeenCalledWith({
            error: expect.objectContaining({
                code: 'INTERNAL_SERVER_ERROR',
                statusCode: 500,
                message: 'Database connection failed unexpectedly.'
            })
        });
    });
});
