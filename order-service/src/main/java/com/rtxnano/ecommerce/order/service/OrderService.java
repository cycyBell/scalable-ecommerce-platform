package com.rtxnano.ecommerce.order.service;

import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.OrderResponseDto;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * ==============================================================================
 * SERVICE INTERFACE: OrderService
 * ==============================================================================
 * Core business engine coordinating checkout, stock reservation SAGA rollback,
 * atomic Transactional Outbox event creation, and state transitions.
 */
public interface OrderService {

    /**
     * Executes end-to-end checkout with SAGA stock reservation, ACID order & outbox persistence,
     * cart cleanup, and distributed idempotency caching.
     */
    OrderResponseDto createOrder(CreateOrderRequestDto request,
                                 UserPrincipal principal,
                                 String bearerToken,
                                 String idempotencyKey);

    /**
     * Atomically persists the Order, line items, and the OutboxEvent in the same database transaction.
     */
    OrderResponseDto saveOrderAndOutbox(CreateOrderRequestDto request, UUID userId, CartResponseDto cart);

    /**
     * Retrieves an order by ID with line items, enforcing ownership and admin permissions.
     */
    OrderResponseDto getOrderById(UUID orderId, UserPrincipal principal);

    /**
     * Retrieves paginated orders for the authenticated user or all orders for admins.
     */
    Page<OrderResponseDto> getUserOrders(UserPrincipal principal, Pageable pageable);

    /**
     * Cancels an order, validates state transition, releases stock, and generates Outbox event.
     */
    OrderResponseDto cancelOrder(UUID orderId, UserPrincipal principal, String bearerToken, String reason);
}
