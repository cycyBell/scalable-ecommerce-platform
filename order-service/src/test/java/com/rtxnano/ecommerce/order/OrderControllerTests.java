package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.controller.OrderController;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.dto.CancelOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.OrderItemResponseDto;
import com.rtxnano.ecommerce.order.dto.OrderResponseDto;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import com.rtxnano.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderController Unit Tests")
class OrderControllerTests {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private UserPrincipal customerPrincipal;
    private UUID testUserId;
    private UUID testOrderId;
    private OrderResponseDto sampleResponse;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testOrderId = UUID.randomUUID();
        customerPrincipal = new UserPrincipal(
                testUserId,
                "buyer@test.com",
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        OrderItemResponseDto item = new OrderItemResponseDto(
                UUID.randomUUID(), "prod-1", "Gaming Mouse", new BigDecimal("50.00"), 2, new BigDecimal("100.00")
        );

        sampleResponse = new OrderResponseDto(
                testOrderId,
                testUserId,
                OrderStatus.PENDING,
                new BigDecimal("100.00"),
                "USD",
                "123 Main St",
                0L,
                Instant.now(),
                Instant.now(),
                List.of(item)
        );
    }

    @Test
    @DisplayName("POST /orders should return 201 Created and OrderResponseDto")
    void shouldCreateOrderSuccessfully() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Main St", "USD");
        String idempotencyKey = "key-test-123";
        String authHeader = "Bearer jwt-test-token";

        when(orderService.createOrder(eq(request), eq(customerPrincipal), eq("jwt-test-token"), eq(idempotencyKey)))
                .thenReturn(sampleResponse);

        ResponseEntity<OrderResponseDto> response = orderController.createOrder(
                request, idempotencyKey, authHeader, customerPrincipal
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testOrderId, response.getBody().getId());
        assertEquals(OrderStatus.PENDING, response.getBody().getStatus());
        verify(orderService).createOrder(eq(request), eq(customerPrincipal), eq("jwt-test-token"), eq(idempotencyKey));
    }

    @Test
    @DisplayName("GET /orders/{id} should return 200 OK and OrderResponseDto")
    void shouldGetOrderByIdSuccessfully() {
        when(orderService.getOrderById(testOrderId, customerPrincipal)).thenReturn(sampleResponse);

        ResponseEntity<OrderResponseDto> response = orderController.getOrderById(testOrderId, customerPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testOrderId, response.getBody().getId());
        verify(orderService).getOrderById(testOrderId, customerPrincipal);
    }

    @Test
    @DisplayName("GET /orders should return 200 OK with paginated order results")
    void shouldGetUserOrdersSuccessfully() {
        Page<OrderResponseDto> pageResult = new PageImpl<>(List.of(sampleResponse));
        when(orderService.getUserOrders(eq(customerPrincipal), any(Pageable.class))).thenReturn(pageResult);

        ResponseEntity<Page<OrderResponseDto>> response = orderController.getUserOrders(
                0, 20, "createdAt", "desc", customerPrincipal
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        verify(orderService).getUserOrders(eq(customerPrincipal), any(Pageable.class));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/cancel should return 200 OK with cancelled order")
    void shouldCancelOrderSuccessfully() {
        CancelOrderRequestDto cancelRequest = new CancelOrderRequestDto("Found cheaper elsewhere");
        String authHeader = "Bearer jwt-test-token";

        OrderResponseDto cancelledResponse = new OrderResponseDto(
                testOrderId,
                testUserId,
                OrderStatus.CANCELLED,
                new BigDecimal("100.00"),
                "USD",
                "123 Main St",
                1L,
                Instant.now(),
                Instant.now(),
                Collections.emptyList()
        );

        when(orderService.cancelOrder(eq(testOrderId), eq(customerPrincipal), eq("jwt-test-token"), eq("Found cheaper elsewhere")))
                .thenReturn(cancelledResponse);

        ResponseEntity<OrderResponseDto> response = orderController.cancelOrder(
                testOrderId, cancelRequest, authHeader, customerPrincipal
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(OrderStatus.CANCELLED, response.getBody().getStatus());
        verify(orderService).cancelOrder(eq(testOrderId), eq(customerPrincipal), eq("jwt-test-token"), eq("Found cheaper elsewhere"));
    }
}
