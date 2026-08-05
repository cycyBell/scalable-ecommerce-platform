package com.rtxnano.ecommerce.user.exception;

/**
 * Custom runtime exception thrown when rate limits are exceeded (HTTP 429 Too Many Requests).
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
