package com.rtxnano.ecommerce.order.client;

import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.client.exception.CartServiceException;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ==============================================================================
 * CLIENT IMPLEMENTATION: CartServiceClientImpl
 * ==============================================================================
 * Synchronously communicates with Shopping Cart Service (:8002) over HTTP.
 * Forwards Bearer JWT tokens to preserve user identity and permissions.
 */
@Service
public class CartServiceClientImpl implements CartServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CartServiceClientImpl.class);

    private final RestClient cartRestClient;

    public CartServiceClientImpl(@Qualifier("cartRestClient") RestClient cartRestClient) {
        this.cartRestClient = cartRestClient;
    }

    @Override
    public CartResponseDto getCart(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new CartServiceException("Bearer token is required to fetch user cart", 401);
        }

        String authHeader = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;

        try {
            CartResponseDto response = cartRestClient.get()
                    .uri("/cart")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 404) {
                            throw new EmptyCartException("Shopping cart is empty or does not exist");
                        }
                        throw new CartServiceException("Cart Service client error: HTTP " + code, code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new CartServiceException("Cart Service server error: HTTP " + res.getStatusCode().value(), res.getStatusCode().value());
                    })
                    .body(CartResponseDto.class);

            if (response == null || response.isEmpty()) {
                throw new EmptyCartException("Shopping cart contains no items for checkout");
            }

            return response;
        } catch (EmptyCartException e) {
            throw e;
        } catch (CartServiceException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Failed to connect to Shopping Cart Service: {}", ex.getMessage());
            throw new CartServiceException("Shopping Cart Service is currently unreachable: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void clearCart(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return;
        }

        String authHeader = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;

        try {
            cartRestClient.delete()
                    .uri("/cart")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Successfully cleared shopping cart post-checkout");
        } catch (Exception ex) {
            log.warn("Failed to clear shopping cart post-checkout (non-fatal): {}", ex.getMessage());
        }
    }
}
