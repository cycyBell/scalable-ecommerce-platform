package com.rtxnano.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.client.dto.ProductResponseDto;
import com.rtxnano.ecommerce.order.client.exception.CatalogServiceException;
import com.rtxnano.ecommerce.order.client.exception.InsufficientStockException;
import com.rtxnano.ecommerce.order.client.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CatalogServiceClient Unit Tests")
class CatalogServiceClientTests {

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private CatalogServiceClient catalogServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        catalogServiceClient = new CatalogServiceClient(restClient);
    }

    @Test
    @DisplayName("Should successfully fetch product details by ID")
    void shouldFetchProductSuccessfully() throws Exception {
        ProductResponseDto product = new ProductResponseDto("prod-1", "Mechanical Keyboard", new BigDecimal("120.00"), 10);

        mockServer.expect(requestTo("http://localhost:8000/products/prod-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(product), MediaType.APPLICATION_JSON));

        ProductResponseDto result = catalogServiceClient.getProduct("prod-1");

        assertNotNull(result);
        assertEquals("prod-1", result.getId());
        assertEquals("Mechanical Keyboard", result.getName());
        assertEquals(10, result.getStockQuantity());
        assertTrue(result.hasStockFor(5));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw ProductNotFoundException when product is 404")
    void shouldThrowProductNotFoundException() {
        mockServer.expect(requestTo("http://localhost:8000/products/unknown-prod"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ProductNotFoundException ex = assertThrows(ProductNotFoundException.class,
                () -> catalogServiceClient.getProduct("unknown-prod"));
        assertEquals("unknown-prod", ex.getProductId());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully reserve stock (negative quantity change)")
    void shouldReserveStockSuccessfully() throws Exception {
        ProductResponseDto updatedProduct = new ProductResponseDto("prod-1", "Mechanical Keyboard", new BigDecimal("120.00"), 8);

        mockServer.expect(requestTo("http://localhost:8000/products/prod-1/stock"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt"))
                .andExpect(content().json("{\"quantity_change\": -2}"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(updatedProduct), MediaType.APPLICATION_JSON));

        catalogServiceClient.reserveStock("prod-1", 2, "test-jwt");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when Catalog Service returns 409 Conflict")
    void shouldThrowInsufficientStockExceptionOn409() {
        mockServer.expect(requestTo("http://localhost:8000/products/prod-1/stock"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"quantity_change\": -50}"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> catalogServiceClient.reserveStock("prod-1", 50, "test-jwt"));
        assertEquals("prod-1", ex.getProductId());
        assertEquals(50, ex.getRequested());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully release stock (positive quantity change)")
    void shouldReleaseStockSuccessfully() throws Exception {
        ProductResponseDto updatedProduct = new ProductResponseDto("prod-1", "Mechanical Keyboard", new BigDecimal("120.00"), 12);

        mockServer.expect(requestTo("http://localhost:8000/products/prod-1/stock"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"quantity_change\": 2}"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(updatedProduct), MediaType.APPLICATION_JSON));

        catalogServiceClient.releaseStock("prod-1", 2, "test-jwt");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw CatalogServiceException on 500 server error")
    void shouldThrowCatalogServiceExceptionOn500() {
        mockServer.expect(requestTo("http://localhost:8000/products/prod-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(CatalogServiceException.class, () -> catalogServiceClient.getProduct("prod-1"));
        mockServer.verify();
    }
}
