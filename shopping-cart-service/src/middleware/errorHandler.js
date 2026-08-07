/**
 * ==============================================================================
 * MODULE: Centralized Error Handling & Custom Error Hierarchy (`src/middleware/errorHandler.js`)
 * ==============================================================================
 * 
 * WHY IS CENTRALIZED ERROR HANDLING VITAL FOR MICROSERVICES?
 * 1. Consistency: Standardizes all API error response structures across microservices
 *    ({ error: { code, message, statusCode, timestamp, path } }).
 * 2. Security: Prevents sensitive stack traces, database schema names, or environment
 *    secrets from leaking to clients in production.
 * 3. Operability: Distinguishes between expected "Operational Errors" (e.g. 400 Bad Request,
 *    401 Unauthorized) and unexpected "Programmer Bugs" (e.g. NullPointer, TypeError).
 * ==============================================================================
 */

/**
 * Base Custom API Error Class
 * Extends the native JavaScript Error object with HTTP status codes and error codes.
 */
class ApiError extends Error {
    /**
     * @param {string} message Human-readable error explanation.
     * @param {number} statusCode HTTP status code (e.g. 400, 401, 404, 500).
     * @param {string} errorCode Machine-readable error string (e.g. 'UNAUTHORIZED').
     * @param {boolean} isOperational True for expected client/business errors, false for system bugs.
     */
    constructor(message, statusCode = 500, errorCode = 'INTERNAL_SERVER_ERROR', isOperational = true) {
        super(message);
        this.name = this.constructor.name;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.isOperational = isOperational;
        
        // Capture V8 stack trace excluding constructor call
        Error.captureStackTrace(this, this.constructor);
    }
}

/**
 * HTTP 400 Bad Request Error
 * Used when incoming request validation fails (e.g. negative quantity, invalid UUID format).
 */
class BadRequestError extends ApiError {
    constructor(message = 'Invalid request parameters or payload format') {
        super(message, 400, 'BAD_REQUEST', true);
    }
}

/**
 * HTTP 401 Unauthorized Error
 * Used when authentication fails (missing, invalid, or expired JWT access token).
 */
class UnauthorizedError extends ApiError {
    constructor(message = 'Authentication required. Please provide a valid Bearer token or guest ID.') {
        super(message, 401, 'UNAUTHORIZED', true);
    }
}

/**
 * HTTP 403 Forbidden Error
 * Used when an authenticated user lacks required roles/permissions.
 */
class ForbiddenError extends ApiError {
    constructor(message = 'Access denied. Insufficient permissions for this resource.') {
        super(message, 403, 'FORBIDDEN', true);
    }
}

/**
 * HTTP 404 Not Found Error
 * Used when a requested resource (e.g. cart item, product) or route does not exist.
 */
class NotFoundError extends ApiError {
    constructor(message = 'The requested resource or endpoint was not found') {
        super(message, 404, 'NOT_FOUND', true);
    }
}

/**
 * HTTP 409 Conflict Error
 * Used when a state conflict occurs (e.g. merging a guest cart that no longer exists).
 */
class ConflictError extends ApiError {
    constructor(message = 'Resource state conflict occurred') {
        super(message, 409, 'CONFLICT', true);
    }
}

/**
 * HTTP 429 Rate Limit Exceeded Error
 */
class RateLimitExceededError extends ApiError {
    constructor(message = 'Too many requests. Please slow down.') {
        super(message, 429, 'RATE_LIMIT_EXCEEDED', true);
    }
}

/**
 * Express 404 Not Found Middleware Handler.
 * Placed at the end of the route middleware chain to catch unmapped HTTP endpoints.
 */
function notFoundHandler(req, res, next) {
    const error = new NotFoundError(`Endpoint '${req.method} ${req.originalUrl}' does not exist on this server.`);
    next(error);
}

/**
 * Global Express Error Handling Middleware.
 * Must have 4 arguments `(err, req, res, next)` for Express to recognize it as an error handler.
 */
function errorHandler(err, req, res, next) {
    let error = err;

    // Handle JWT Library Specific Errors
    if (err.name === 'JsonWebTokenError') {
        error = new UnauthorizedError('Invalid authentication token signature.');
    } else if (err.name === 'TokenExpiredError') {
        error = new UnauthorizedError('Authentication token has expired. Please refresh your session.');
    } else if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
        // Handle malformed JSON body errors thrown by express.json()
        error = new BadRequestError('Malformed JSON payload format in request body.');
    } else if (!(error instanceof ApiError)) {
        // Wrap unexpected third-party or runtime errors into a 500 ApiError
        const statusCode = err.statusCode || err.status || 500;
        const message = err.message || 'An unexpected internal server error occurred.';
        error = new ApiError(message, statusCode, 'INTERNAL_SERVER_ERROR', false);
    }

    // Log unexpected non-operational system errors for diagnostics
    if (!error.isOperational || error.statusCode >= 500) {
        console.error(`[CRITICAL ERROR] ${req.method} ${req.originalUrl}:`, err);
    }

    // Construct standardized API JSON response payload
    const responsePayload = {
        error: {
            code: error.errorCode,
            message: error.message,
            statusCode: error.statusCode,
            timestamp: new Date().toISOString(),
            path: req.originalUrl || req.url
        }
    };

    // Attach stack trace only in development mode for debugging
    if (process.env.NODE_ENV === 'development' && !error.isOperational) {
        responsePayload.error.stack = error.stack;
    }

    res.status(error.statusCode).json(responsePayload);
}

module.exports = {
    ApiError,
    BadRequestError,
    UnauthorizedError,
    ForbiddenError,
    NotFoundError,
    ConflictError,
    RateLimitExceededError,
    notFoundHandler,
    errorHandler
};
