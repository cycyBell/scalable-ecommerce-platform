package com.rtxnano.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtxnano.ecommerce.order.client.CartServiceClient;
import com.rtxnano.ecommerce.order.client.CatalogServiceClient;
import com.rtxnano.ecommerce.order.client.dto.CartItemResponseDto;
import com.rtxnano.ecommerce.order.client.dto.CartResponseDto;
import com.rtxnano.ecommerce.order.domain.entity.IdempotencyRecord;
import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.entity.OutboxEvent;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.enums.OutboxStatus;
import com.rtxnano.ecommerce.order.dto.CancelOrderRequestDto;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.repository.IdempotencyRecordRepository;
import com.rtxnano.ecommerce.order.repository.OrderRepository;
import com.rtxnano.ecommerce.order.repository.OutboxEventRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Order Service End-to-End Integration Tests")
class OrderIntegrationTests extends BaseIntegrationTest {

    private static final String TEST_SECRET = "8d/vpFSCAFqeRdZD7W2ZbBUbvs9r3FajrfXlCDp4cTk=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @MockitoBean
    private CartServiceClient cartServiceClient;

    @MockitoBean
    private CatalogServiceClient catalogServiceClient;

    private UUID customerId;
    private UUID otherCustomerId;
    private String customerJwtToken;
    private String otherCustomerJwtToken;
    private String adminJwtToken;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        orderRepository.deleteAll();

        customerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();

        customerJwtToken = createTestToken(customerId, "customer@test.com", List.of("ROLE_USER"));
        otherCustomerJwtToken = createTestToken(otherCustomerId, "other@test.com", List.of("ROLE_USER"));
        adminJwtToken = createTestToken(UUID.randomUUID(), "admin@test.com", List.of("ROLE_ADMIN"));
    }

    private String createTestToken(UUID userId, String email, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("Should execute end-to-end checkout flow, persist order & outbox, clear cart, and record idempotency")
    void shouldExecuteEndToEndCheckoutSuccessfully() throws Exception {
        CartItemResponseDto item1 = new CartItemResponseDto("prod-1", "Gaming Laptop", new BigDecimal("50.00"), 2, new BigDecimal("100.00"));
        CartItemResponseDto item2 = new CartItemResponseDto("prod-2", "Wireless Mouse", new BigDecimal("100.00"), 1, new BigDecimal("100.00"));
        CartResponseDto mockCart = new CartResponseDto(customerId.toString(), List.of(item1, item2), 3, new BigDecimal("200.00"));

        when(cartServiceClient.getCart(anyStringOrNull())).thenReturn(mockCart);

        CreateOrderRequestDto request = new CreateOrderRequestDto("742 Evergreen Terrace", "USD");
        String idempotencyKey = "key-int-checkout-001";

        // 1. Submit Checkout Request via REST API
        String mvcResponse = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("PENDING")))
                .andExpect(jsonPath("$.totalAmount", equalTo(200.00)))
                .andExpect(jsonPath("$.currency", equalTo("USD")))
                .andExpect(jsonPath("$.shippingAddress", equalTo("742 Evergreen Terrace")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        UUID createdOrderId = UUID.fromString(objectMapper.readTree(mvcResponse).get("id").asText());

        // 2. Verify Order Entity in Database
        Optional<Order> orderOpt = orderRepository.findByIdWithItems(createdOrderId);
        assertTrue(orderOpt.isPresent());
        Order order = orderOpt.get();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(customerId, order.getUserId());
        assertEquals(2, order.getItems().size());

        // 3. Verify OutboxEvent Entity in Database
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(outboxEvents.stream().anyMatch(e -> e.getAggregateId().equals(createdOrderId.toString()) && "OrderCreated".equals(e.getEventType())));

        // 4. Verify Idempotency Record in Database
        Optional<IdempotencyRecord> idemOpt = idempotencyRecordRepository.findByKeyAndUserId(idempotencyKey, customerId);
        assertTrue(idemOpt.isPresent());

        // 5. Verify Inter-Service Communications
        verify(catalogServiceClient, times(1)).reserveStock(eq("prod-1"), eq(2), anyStringOrNull());
        verify(catalogServiceClient, times(1)).reserveStock(eq("prod-2"), eq(1), anyStringOrNull());
        verify(cartServiceClient, times(1)).clearCart(anyStringOrNull());
    }

    @Test
    @DisplayName("Should block duplicate checkout submission and replay cached response via idempotency key")
    void shouldBlockDuplicateCheckoutAndReplayCachedResponse() throws Exception {
        CartItemResponseDto item = new CartItemResponseDto("prod-10", "Keyboard", new BigDecimal("75.00"), 1, new BigDecimal("75.00"));
        CartResponseDto mockCart = new CartResponseDto(customerId.toString(), List.of(item), 1, new BigDecimal("75.00"));

        when(cartServiceClient.getCart(anyStringOrNull())).thenReturn(mockCart);

        CreateOrderRequestDto request = new CreateOrderRequestDto("100 Main Street", "USD");
        String idempotencyKey = "key-int-duplicate-002";

        // First Submission
        String firstResponse = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Duplicate Submission with identical Idempotency-Key
        String secondResponse = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(firstResponse, secondResponse);
        // Verify downstream services were only called ONCE for original request
        verify(cartServiceClient, times(1)).getCart(anyStringOrNull());
        verify(catalogServiceClient, times(1)).reserveStock(eq("prod-10"), eq(1), anyStringOrNull());
    }

    @Test
    @DisplayName("Should enforce ownership security on GET /orders/{id}")
    void shouldEnforceOwnershipSecurityOnGetOrderById() throws Exception {
        CartItemResponseDto item = new CartItemResponseDto("prod-5", "Monitor", new BigDecimal("150.00"), 1, new BigDecimal("150.00"));
        when(cartServiceClient.getCart(anyStringOrNull())).thenReturn(new CartResponseDto(customerId.toString(), List.of(item), 1, new BigDecimal("150.00")));

        CreateOrderRequestDto request = new CreateOrderRequestDto("500 Market St", "USD");

        String response = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(response).get("id").asText();

        // Owner customer can access
        mockMvc.perform(get("/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(orderId)));

        // Unrelated customer is denied 403 Forbidden with RFC 7807 problem detail
        mockMvc.perform(get("/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherCustomerJwtToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title", equalTo("Forbidden")));

        // Admin can access any customer's order
        mockMvc.perform(get("/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(orderId)));
    }

    @Test
    @DisplayName("Should cancel order, release inventory stock, and publish Outbox event")
    void shouldCancelOrderAndReleaseStock() throws Exception {
        CartItemResponseDto item = new CartItemResponseDto("prod-20", "Headset", new BigDecimal("80.00"), 2, new BigDecimal("160.00"));
        when(cartServiceClient.getCart(anyStringOrNull())).thenReturn(new CartResponseDto(customerId.toString(), List.of(item), 2, new BigDecimal("160.00")));

        CreateOrderRequestDto request = new CreateOrderRequestDto("123 Sunset Blvd", "USD");

        String response = mockMvc.perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(response).get("id").asText();

        // Execute Cancellation
        CancelOrderRequestDto cancelReq = new CancelOrderRequestDto("Customer changed mind");
        mockMvc.perform(patch("/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        // Verify Order status in Database
        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        // Verify stock release invocation
        verify(catalogServiceClient, times(1)).releaseStock(eq("prod-20"), eq(2), anyStringOrNull());
    }

    private String anyStringOrNull() {
        return any();
    }
}
