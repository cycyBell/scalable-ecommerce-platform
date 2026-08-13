package com.rtxnano.ecommerce.order.client.exception;

/**
 * Thrown when communication with the Shopping Cart Service fails.
 */
public class CartServiceException extends RuntimeException {

    private final int statusCode;

    public CartServiceException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public CartServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public CartServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
