package com.rtxnano.ecommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtxnano.ecommerce.order.client.CartServiceClient;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.client.dto.CartItemResponseDto;
import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.event.OrderCancelledEvent;
import com.rtxnano.ecommerce.order.domain.event.OrderCreatedEvent;
import com.rtxnano.ecommerce.order.domain.event.OrderCreatedEvent.OrderItemPayload;
import com.rtxnano.ecommerce.order.domain.statemachine.OrderStateMachine;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.OrderResponseDto;
import com.rtxnano.ecommerce.order.exception.OrderNotFoundException;
import com.rtxnano.ecommerce.order.exception.UnauthorizedOrderAccessException;
import com.rtxnano.ecommerce.order.idempotency.model.IdempotencyResult;
import com.rtxnano.ecommerce.order.idempotency.service.IdempotencyService;
import com.rtxnano.ecommerce.order.repository.OrderRepository;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ==============================================================================
 * SERVICE IMPLEMENTATION: OrderServiceImpl
 * ==============================================================================
 * Core business engine coordinating checkout, stock reservation SAGA rollback,
 * atomic Transactional Outbox event creation, and state transitions.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CartServiceClient cartServiceClient;
    private final CatalogServiceClient catalogServiceClient;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OutboxEventRepository outboxEventRepository,
                            CartServiceClient cartServiceClient,
                            CatalogServiceClient catalogServiceClient,
                            IdempotencyService idempotencyService,
                            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.cartServiceClient = cartServiceClient;
        this.catalogServiceClient = catalogServiceClient;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes end-to-end checkout with SAGA stock reservation, ACID order & outbox persistence,
     * cart cleanup, and distributed idempotency caching.
     */
    @Override
    public OrderResponseDto createOrder(CreateOrderRequestDto request,
                                        UserPrincipal principal,
                                        String bearerToken,
                                        String idempotencyKey) {
        UUID userId = principal.getUserId();
        String requestPayloadJson = serializePayload(request);
        String requestHash = idempotencyService.computeRequestHash(requestPayloadJson);

        // 1. Idempotency Gatekeeper: Check for duplicate submission or replay cached response
        IdempotencyResult idemResult = idempotencyService.checkOrLock(idempotencyKey, userId, requestHash);
        if (idemResult.isCompleted()) {
            log.info("Replaying cached order response for idempotency key '{}'", idempotencyKey);
            return deserializePayload(idemResult.cachedResponse(), OrderResponseDto.class);
        }

        List<ReservedItemTracker> reservedTrackers = new ArrayList<>();

        try {
            // 2. Fetch customer cart items
            CartResponseDto cart = cartServiceClient.getCart(bearerToken);
            if (cart == null || cart.isEmpty()) {
                throw new EmptyCartException("Shopping cart is empty; cannot create order");
            }

            // 3. SAGA Stock Reservation Loop with compensation tracking
            for (CartItemResponseDto item : cart.getItems()) {
                catalogServiceClient.reserveStock(item.getProductId(), item.getQuantity(), bearerToken);
                reservedTrackers.add(new ReservedItemTracker(item.getProductId(), item.getQuantity()));
            }

            // 4. Atomic PostgreSQL Transaction: Persist Order + OutboxEvent
            OrderResponseDto responseDto = saveOrderAndOutbox(request, userId, cart);

            // 5. Post-Commit Actions: Clear Cart & Complete Idempotency
            cartServiceClient.clearCart(bearerToken);

            String serializedResponse = serializePayload(responseDto);
            idempotencyService.complete(idempotencyKey, userId, serializedResponse);

            log.info("Successfully created order '{}' for user '{}' with total amount {}",
                    responseDto.getId(), userId, responseDto.getTotalAmount());
            return responseDto;

        } catch (Exception ex) {
            log.error("Order creation failed for user '{}'. Triggering SAGA compensation rollback: {}",
                    userId, ex.getMessage());

            // SAGA Compensating Transaction: Release all previously reserved stock
            for (ReservedItemTracker tracker : reservedTrackers) {
                try {
                    catalogServiceClient.releaseStock(tracker.productId(), tracker.quantity(), bearerToken);
                } catch (Exception rollbackEx) {
                    log.error("Failed to release stock for product '{}' during SAGA rollback: {}",
                            tracker.productId(), rollbackEx.getMessage());
                }
            }

            // Release idempotency lock so user can retry after fixing errors
            idempotencyService.unlock(idempotencyKey, userId);
            throw ex;
        }
    }

    /**
     * Atomically persists the Order, line items, and the OutboxEvent in the same database transaction.
     */
    @Override
    @Transactional
    public OrderResponseDto saveOrderAndOutbox(CreateOrderRequestDto request, UUID userId, CartResponseDto cart) {
        Order order = new Order(null, userId, request.getShippingAddress(), request.getCurrency());

        for (CartItemResponseDto item : cart.getItems()) {
            OrderItem orderItem = new OrderItem(
                    item.getProductId(),
                    item.getTitle() != null ? item.getTitle() : "Product " + item.getProductId(),
                    item.getPrice(),
                    item.getQuantity()
            );
            order.addItem(orderItem);
        }

        Order savedOrder = orderRepository.saveAndFlush(order);

        // Build domain event payload for asynchronous publishing via Transactional Outbox
        List<OrderItemPayload> itemPayloads = savedOrder.getItems().stream()
                .map(i -> new OrderItemPayload(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getSubtotal()))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTotalAmount(),
                savedOrder.getCurrency(),
                savedOrder.getShippingAddress(),
                itemPayloads,
                savedOrder.getCreatedAt()
        );

        String eventPayloadJson = serializePayload(event);
        OutboxEvent outboxEvent = new OutboxEvent(
                "ORDER",
                savedOrder.getId().toString(),
                "OrderCreated",
                eventPayloadJson
        );

        outboxEventRepository.saveAndFlush(outboxEvent);

        return OrderResponseDto.fromEntity(savedOrder);
    }

    /**
     * Retrieves an order by ID with line items, enforcing ownership and admin permissions.
     */
    @Override
    @Transactional(readOnly = true)
    public OrderResponseDto getOrderById(UUID orderId, UserPrincipal principal) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateOwnership(order, principal);
        return OrderResponseDto.fromEntity(order);
    }

    /**
     * Retrieves paginated orders for the authenticated user or all orders for admins.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getUserOrders(UserPrincipal principal, Pageable pageable) {
        if (principal.isAdmin()) {
            return orderRepository.findAll(pageable).map(OrderResponseDto::fromEntity);
        }
        return orderRepository.findByUserId(principal.getUserId(), pageable).map(OrderResponseDto::fromEntity);
    }

    /**
     * Cancels an order, validates state transition, releases stock, and generates Outbox event.
     */
    @Override
    @Transactional
    public OrderResponseDto cancelOrder(UUID orderId, UserPrincipal principal, String bearerToken, String reason) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateOwnership(order, principal);

        // Validate state machine rule (e.g. PENDING -> CANCELLED or PAID -> CANCELLED)
        OrderStateMachine.validateTransition(order.getStatus(), OrderStatus.CANCELLED);

        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.saveAndFlush(order);

        // Release inventory stock in Product Catalog Service
        for (OrderItem item : updatedOrder.getItems()) {
            try {
                catalogServiceClient.releaseStock(item.getProductId(), item.getQuantity(), bearerToken);
            } catch (Exception ex) {
                log.error("Failed to release stock for product '{}' on order cancellation: {}",
                        item.getProductId(), ex.getMessage());
            }
        }

        // Generate and persist OrderCancelled Outbox Event
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                updatedOrder.getId(),
                updatedOrder.getUserId(),
                (reason != null && !reason.isBlank()) ? reason : "Customer requested cancellation",
                Instant.now()
        );

        OutboxEvent outboxEvent = new OutboxEvent(
                "ORDER",
                updatedOrder.getId().toString(),
                "OrderCancelled",
                serializePayload(cancelledEvent)
        );
        outboxEventRepository.saveAndFlush(outboxEvent);

        log.info("Successfully cancelled order '{}' for user '{}'", orderId, principal.getUserId());
        return OrderResponseDto.fromEntity(updatedOrder);
    }

    private void validateOwnership(Order order, UserPrincipal principal) {
        if (principal.isAdmin()) {
            return; // Admins can access any order
        }
        if (!order.getUserId().equals(principal.getUserId())) {
            throw new UnauthorizedOrderAccessException(order.getId(), principal.getUserId());
        }
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize domain payload", e);
        }
    }

    private <T> T deserializePayload(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize domain payload", e);
        }
    }

    private record ReservedItemTracker(String productId, Integer quantity) {}
}
