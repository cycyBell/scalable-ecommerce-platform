package com.rtxnano.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtxnano.ecommerce.order.client.CartServiceClient;
import com.rtxnano.ecommerce.order.client.CartServiceClientImpl;
import com.rtxnano.ecommerce.order.client.dto.CartItemResponseDto;
import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.client.exception.CartServiceException;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CartServiceClient Unit Tests")
class CartServiceClientTests {

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private CartServiceClient cartServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8002");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        cartServiceClient = new CartServiceClientImpl(restClient);
    }

    @Test
    @DisplayName("Should successfully fetch enriched cart when items exist")
    void shouldFetchCartSuccessfully() throws Exception {
        CartItemResponseDto item = new CartItemResponseDto("prod-1", "Gaming Monitor", new BigDecimal("350.00"), 1, new BigDecimal("350.00"));
        CartResponseDto cartResponse = new CartResponseDto("cart:user-123", List.of(item), 1, new BigDecimal("350.00"));

        mockServer.expect(requestTo("http://localhost:8002/cart"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt-token"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(cartResponse), MediaType.APPLICATION_JSON));

        CartResponseDto result = cartServiceClient.getCart("test-jwt-token");

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.getItems().size());
        assertEquals("Gaming Monitor", result.getItems().get(0).getTitle());
        assertEquals(new BigDecimal("350.00"), result.getTotalAmount());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw EmptyCartException when Cart Service returns 404")
    void shouldThrowEmptyCartExceptionOn404() {
        mockServer.expect(requestTo("http://localhost:8002/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(EmptyCartException.class, () -> cartServiceClient.getCart("test-jwt-token"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw EmptyCartException when Cart Service returns empty item list")
    void shouldThrowEmptyCartExceptionOnEmptyItems() throws Exception {
        CartResponseDto emptyCart = new CartResponseDto("cart:user-123", List.of(), 0, BigDecimal.ZERO);

        mockServer.expect(requestTo("http://localhost:8002/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(emptyCart), MediaType.APPLICATION_JSON));

        assertThrows(EmptyCartException.class, () -> cartServiceClient.getCart("test-jwt-token"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw CartServiceException on Cart Service 500 server error")
    void shouldThrowCartServiceExceptionOn500() {
        mockServer.expect(requestTo("http://localhost:8002/cart"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(CartServiceException.class, () -> cartServiceClient.getCart("test-jwt-token"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should execute DELETE /cart to clear cart post checkout")
    void shouldClearCartSuccessfully() {
        mockServer.expect(requestTo("http://localhost:8002/cart"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        cartServiceClient.clearCart("test-jwt-token");
        mockServer.verify();
    }
}
