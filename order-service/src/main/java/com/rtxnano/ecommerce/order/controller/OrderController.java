package com.rtxnano.ecommerce.order.controller;

import com.rtxnano.ecommerce.order.dto.CancelOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.OrderResponseDto;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import com.rtxnano.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ==============================================================================
 * CONTROLLER: OrderController
 * ==============================================================================
 * REST API for creating orders (checkout), querying customer order history,
 * looking up order details, and executing state machine cancellations.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order by checking out the current user's shopping cart.
     * Enforces distributed idempotency via optional 'Idempotency-Key' header.
     */
    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody CreateOrderRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = extractToken(authHeader);
        log.info("Received order creation request from user '{}' [idempotencyKey={}]",
                principal.getUserId(), idempotencyKey);

        OrderResponseDto response = orderService.createOrder(request, principal, token, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves full details for a specific order by ID.
     * Ownership is enforced: customers can only view their own orders; admins can view any order.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("User '{}' fetching order details for '{}'", principal.getUserId(), id);
        OrderResponseDto response = orderService.getOrderById(id, principal);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves paginated order history for the authenticated user (or all orders if admin).
     */
    @GetMapping
    public ResponseEntity<Page<OrderResponseDto>> getUserOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @AuthenticationPrincipal UserPrincipal principal) {

        Sort sort = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort);
        log.debug("User '{}' fetching orders page {} (size={})", principal.getUserId(), page, size);

        Page<OrderResponseDto> response = orderService.getUserOrders(principal, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an order, releases reserved stock in Product Catalog Service,
     * and publishes an OrderCancelledEvent to RabbitMQ.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelOrderRequestDto cancelRequest,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = extractToken(authHeader);
        String reason = cancelRequest != null ? cancelRequest.reason() : null;

        log.info("User '{}' requested cancellation for order '{}' [reason='{}']",
                principal.getUserId(), id, reason);

        OrderResponseDto response = orderService.cancelOrder(id, principal, token, reason);
        return ResponseEntity.ok(response);
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader.trim();
    }
}
