package com.rtxnano.ecommerce.order.client;

import com.rtxnano.ecommerce.order.client.dto.ProductResponseDto;
import com.rtxnano.ecommerce.order.client.dto.StockAdjustmentRequestDto;
import com.rtxnano.ecommerce.order.client.exception.CatalogServiceException;
import com.rtxnano.ecommerce.order.client.exception.InsufficientStockException;
import com.rtxnano.ecommerce.order.client.exception.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * ==============================================================================
 * CLIENT IMPLEMENTATION: CatalogServiceClientImpl
 * ==============================================================================
 * Synchronously communicates with Product Catalog Service (:8000) to fetch live
 * catalog metadata and atomically adjust inventory stock levels.
 */
@Service
public class CatalogServiceClientImpl implements CatalogServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogServiceClientImpl.class);

    private final RestClient catalogRestClient;

    public CatalogServiceClientImpl(@Qualifier("catalogRestClient") RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    @Override
    public ProductResponseDto getProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID must not be empty");
        }

        try {
            return catalogRestClient.get()
                    .uri("/products/{productId}", productId.trim())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ProductNotFoundException(productId);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new CatalogServiceException("Catalog client error: HTTP " + res.getStatusCode().value(), res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new CatalogServiceException("Catalog server error: HTTP " + res.getStatusCode().value(), res.getStatusCode().value());
                    })
                    .body(ProductResponseDto.class);
        } catch (ProductNotFoundException | CatalogServiceException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Failed to query Product Catalog Service for productId '{}': {}", productId, ex.getMessage());
            throw new CatalogServiceException("Product Catalog Service is currently unreachable: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ProductResponseDto adjustStock(String productId, int quantityChange, String bearerToken) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID must not be empty");
        }

        String authHeader = bearerToken != null && !bearerToken.isBlank()
                ? (bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken)
                : null;

        try {
            var requestSpec = catalogRestClient.patch()
                    .uri("/products/{productId}/stock", productId.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StockAdjustmentRequestDto(quantityChange));

            if (authHeader != null) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, authHeader);
            }

            return requestSpec.retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new ProductNotFoundException(productId);
                    })
                    .onStatus(status -> status.value() == 409, (req, res) -> {
                        throw new InsufficientStockException(productId, Math.abs(quantityChange), 0);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new CatalogServiceException("Catalog stock update client error: HTTP " + res.getStatusCode().value(), res.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new CatalogServiceException("Catalog stock update server error: HTTP " + res.getStatusCode().value(), res.getStatusCode().value());
                    })
                    .body(ProductResponseDto.class);
        } catch (ProductNotFoundException | InsufficientStockException | CatalogServiceException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Failed to adjust stock for product '{}' (delta={}): {}", productId, quantityChange, ex.getMessage());
            throw new CatalogServiceException("Product Catalog Service is currently unreachable: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void reserveStock(String productId, int quantity, String bearerToken) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to reserve must be greater than 0");
        }
        adjustStock(productId, -quantity, bearerToken);
    }

    @Override
    public void releaseStock(String productId, int quantity, String bearerToken) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to release must be greater than 0");
        }
        adjustStock(productId, quantity, bearerToken);
    }
}
