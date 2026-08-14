package com.rtxnano.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rtxnano.ecommerce.order.client.CartServiceClient;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.client.dto.CartItemResponseDto;
import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
import com.rtxnano.ecommerce.order.client.exception.InsufficientStockException;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.exception.InvalidOrderStateTransitionException;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.OrderResponseDto;
import com.rtxnano.ecommerce.order.exception.OrderNotFoundException;
import com.rtxnano.ecommerce.order.exception.UnauthorizedOrderAccessException;
import com.rtxnano.ecommerce.order.idempotency.model.IdempotencyResult;
import com.rtxnano.ecommerce.order.idempotency.service.IdempotencyService;
import com.rtxnano.ecommerce.order.repository.OrderRepository;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import com.rtxnano.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService Business Logic & SAGA Unit Tests")
class OrderServiceTests {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private CartServiceClient cartServiceClient;

    @Mock
    private CatalogServiceClient catalogServiceClient;

    @Mock
    private IdempotencyService idempotencyService;

    private ObjectMapper objectMapper;
    private OrderService orderService;

    private UUID testUserId;
    private UserPrincipal customerPrincipal;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        orderService = new OrderService(
                orderRepository,
                outboxEventRepository,
                cartServiceClient,
                catalogServiceClient,
                idempotencyService,
                objectMapper
        );

        testUserId = UUID.randomUUID();
        customerPrincipal = new UserPrincipal(testUserId, "buyer@example.com", Set.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin@example.com", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Should successfully create order, reserve stock, save outbox, and clear cart")
    void shouldCreateOrderSuccessfully() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Main St, New York, NY", "USD");
        String token = "jwt-test-token";
        String idempotencyKey = "key-123";

        when(idempotencyService.computeRequestHash(anyString())).thenReturn("hash-123");
        when(idempotencyService.checkOrLock(idempotencyKey, testUserId, "hash-123"))
                .thenReturn(IdempotencyResult.proceed());

        CartItemResponseDto item1 = new CartItemResponseDto("prod-1", "Keyboard", new BigDecimal("100.00"), 1, new BigDecimal("100.00"));
        CartItemResponseDto item2 = new CartItemResponseDto("prod-2", "Mouse", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartResponseDto cart = new CartResponseDto("cart:user", List.of(item1, item2), 3, new BigDecimal("200.00"));

        when(cartServiceClient.getCart(token)).thenReturn(cart);

        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OrderResponseDto result = orderService.createOrder(request, customerPrincipal, token, idempotencyKey);

        assertNotNull(result);
        assertEquals(new BigDecimal("200.00"), result.getTotalAmount());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(2, result.getItems().size());

        // Verify SAGA stock reservations
        verify(catalogServiceClient).reserveStock("prod-1", 1, token);
        verify(catalogServiceClient).reserveStock("prod-2", 2, token);

        // Verify Outbox Event created
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).saveAndFlush(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals("ORDER", outbox.getAggregateType());
        assertEquals("OrderCreated", outbox.getEventType());

        // Verify Cart cleared
        verify(cartServiceClient).clearCart(token);

        // Verify Idempotency completed
        verify(idempotencyService).complete(eq(idempotencyKey), eq(testUserId), anyString());
    }

    @Test
    @DisplayName("Should replay cached response immediately on idempotency cache hit")
    void shouldReplayCachedResponseOnIdempotencyHit() throws Exception {
        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Main St", "USD");
        String idempotencyKey = "key-cached-123";

        OrderResponseDto cachedOrder = new OrderResponseDto();
        cachedOrder.setId(UUID.randomUUID());
        cachedOrder.setUserId(testUserId);
        cachedOrder.setStatus(OrderStatus.PENDING);
        cachedOrder.setTotalAmount(new BigDecimal("150.00"));
        String cachedJson = objectMapper.writeValueAsString(cachedOrder);

        when(idempotencyService.computeRequestHash(anyString())).thenReturn("hash-123");
        when(idempotencyService.checkOrLock(idempotencyKey, testUserId, "hash-123"))
                .thenReturn(IdempotencyResult.completed(cachedJson));

        OrderResponseDto result = orderService.createOrder(request, customerPrincipal, "token", idempotencyKey);

        assertNotNull(result);
        assertEquals(cachedOrder.getId(), result.getId());
        assertEquals(new BigDecimal("150.00"), result.getTotalAmount());

        // Should NOT call cart, catalog, or database
        verify(cartServiceClient, never()).getCart(any());
        verify(catalogServiceClient, never()).reserveStock(any(), any(Integer.class), any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should trigger SAGA compensating rollback and release stock if reservation fails midway")
    void shouldTriggerSagaCompensationOnStockFailure() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Main St", "USD");
        String token = "jwt-test-token";
        String idempotencyKey = "key-fail-123";

        when(idempotencyService.computeRequestHash(anyString())).thenReturn("hash-123");
        when(idempotencyService.checkOrLock(idempotencyKey, testUserId, "hash-123"))
                .thenReturn(IdempotencyResult.proceed());

        CartItemResponseDto item1 = new CartItemResponseDto("prod-1", "Keyboard", new BigDecimal("100.00"), 1, new BigDecimal("100.00"));
        CartItemResponseDto item2 = new CartItemResponseDto("prod-2", "Rare Item", new BigDecimal("500.00"), 1, new BigDecimal("500.00"));
        CartResponseDto cart = new CartResponseDto("cart:user", List.of(item1, item2), 2, new BigDecimal("600.00"));

        when(cartServiceClient.getCart(token)).thenReturn(cart);

        // First item succeeds, second item throws InsufficientStockException
        doThrow(new InsufficientStockException("prod-2", 1, 0))
                .when(catalogServiceClient).reserveStock("prod-2", 1, token);

        assertThrows(InsufficientStockException.class,
                () -> orderService.createOrder(request, customerPrincipal, token, idempotencyKey));

        // SAGA COMPENSATING ROLLBACK: release item1 that succeeded!
        verify(catalogServiceClient).releaseStock("prod-1", 1, token);

        // Unlock idempotency key
        verify(idempotencyService).unlock(idempotencyKey, testUserId);

        // Ensure database was NOT written
        verify(orderRepository, never()).saveAndFlush(any());
        verify(outboxEventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw EmptyCartException when attempting checkout with empty cart")
    void shouldThrowWhenCartIsEmpty() {
        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Main St", "USD");
        when(idempotencyService.computeRequestHash(anyString())).thenReturn("hash");
        when(idempotencyService.checkOrLock(any(), any(), any())).thenReturn(IdempotencyResult.proceed());

        when(cartServiceClient.getCart(any())).thenReturn(new CartResponseDto("cart", List.of(), 0, BigDecimal.ZERO));

        assertThrows(EmptyCartException.class,
                () -> orderService.createOrder(request, customerPrincipal, "token", "key"));

        verify(catalogServiceClient, never()).reserveStock(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("Should allow owner or admin to get order by ID")
    void shouldGetOrderByIdForOwnerAndAdmin() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, testUserId, "123 Main St", "USD");

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        OrderResponseDto ownerResult = orderService.getOrderById(orderId, customerPrincipal);
        assertNotNull(ownerResult);
        assertEquals(orderId, ownerResult.getId());

        OrderResponseDto adminResult = orderService.getOrderById(orderId, adminPrincipal);
        assertNotNull(adminResult);
        assertEquals(orderId, adminResult.getId());
    }

    @Test
    @DisplayName("Should forbid other users from accessing another user's order")
    void shouldForbidUnauthorizedUserFromGettingOrder() {
        UUID orderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order order = new Order(orderId, otherUserId, "123 Main St", "USD");

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedOrderAccessException.class,
                () -> orderService.getOrderById(orderId, customerPrincipal));
    }

    @Test
    @DisplayName("Should cancel order, release stock, and save OrderCancelled Outbox event")
    void shouldCancelOrderSuccessfully() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, testUserId, "123 Main St", "USD");
        OrderItem item = new OrderItem("prod-1", "Keyboard", new BigDecimal("100.00"), 2);
        order.addItem(item);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenReturn(order);

        OrderResponseDto cancelled = orderService.cancelOrder(orderId, customerPrincipal, "token", "Changed mind");

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        // Verify stock was released
        verify(catalogServiceClient).releaseStock("prod-1", 2, "token");

        // Verify Outbox Event created
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).saveAndFlush(outboxCaptor.capture());
        assertEquals("OrderCancelled", outboxCaptor.getValue().getEventType());
    }

    @Test
    @DisplayName("Should reject cancellation if order is in a terminal state (DELIVERED)")
    void shouldRejectCancellationForDeliveredOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, testUserId, "123 Main St", "USD");
        order.setStatus(OrderStatus.DELIVERED);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateTransitionException.class,
                () -> orderService.cancelOrder(orderId, customerPrincipal, "token", "Reason"));

        verify(catalogServiceClient, never()).releaseStock(any(), any(Integer.class), any());
    }
}
