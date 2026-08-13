package com.rtxnano.ecommerce.order.client.exception;

/**
 * Thrown when attempting to checkout with an empty shopping cart.
 */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException(String message) {
        super(message);
    }
}
