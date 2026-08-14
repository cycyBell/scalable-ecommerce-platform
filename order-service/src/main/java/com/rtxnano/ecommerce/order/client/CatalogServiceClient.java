package com.rtxnano.ecommerce.order.client;

import com.rtxnano.ecommerce.order.client.dto.ProductResponseDto;

/**
 * Client interface for communicating with Product Catalog Service.
 */
public interface CatalogServiceClient {

    /**
     * Fetches live product details from Product Catalog Service.
     */
    ProductResponseDto getProduct(String productId);

    /**
     * Atomically adjusts stock in Product Catalog Service.
     */
    ProductResponseDto adjustStock(String productId, int quantityChange, String bearerToken);

    /**
     * Reserves inventory stock during checkout (decrements inventory).
     */
    void reserveStock(String productId, int quantity, String bearerToken);

    /**
     * Releases reserved inventory stock during order cancellation (increments inventory).
     */
    void releaseStock(String productId, int quantity, String bearerToken);
}
