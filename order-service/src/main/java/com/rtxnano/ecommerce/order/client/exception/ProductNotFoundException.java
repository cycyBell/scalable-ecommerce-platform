package com.rtxnano.ecommerce.order.client.exception;

/**
 * Thrown when a product ID does not exist in the Product Catalog.
 */
public class ProductNotFoundException extends RuntimeException {

    private final String productId;

    public ProductNotFoundException(String productId) {
        super(String.format("Product with ID '%s' was not found in catalog", productId));
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
