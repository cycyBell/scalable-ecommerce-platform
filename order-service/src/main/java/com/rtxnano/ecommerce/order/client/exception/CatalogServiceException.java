package com.rtxnano.ecommerce.order.client.exception;

/**
 * Thrown when communication with the Product Catalog Service fails.
 */
public class CatalogServiceException extends RuntimeException {

    private final int statusCode;

    public CatalogServiceException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public CatalogServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public CatalogServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
