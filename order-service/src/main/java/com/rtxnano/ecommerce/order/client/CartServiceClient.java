package com.rtxnano.ecommerce.order.client;

import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;

/**
 * Client interface for communicating with Shopping Cart Service.
 */
public interface CartServiceClient {

    /**
     * Retrieves customer's current shopping cart items, verifying non-empty state.
     */
    CartResponseDto getCart(String bearerToken);

    /**
     * Clears shopping cart after successful order creation.
     */
    void clearCart(String bearerToken);
}
