package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.client.exception.CartServiceException;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
import com.rtxnano.ecommerce.order.client.exception.InsufficientStockException;
import com.rtxnano.ecommerce.order.client.exception.ProductNotFoundException;
import com.rtxnano.ecommerce.order.controller.OrderController;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.exception.InvalidOrderStateTransitionException;
import com.rtxnano.ecommerce.order.dto.CreateOrderRequestDto;
import com.rtxnano.ecommerce.order.exception.GlobalExceptionHandler;
import com.rtxnano.ecommerce.order.exception.OrderNotFoundException;
import com.rtxnano.ecommerce.order.exception.UnauthorizedOrderAccessException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyConflictException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyPayloadMismatchException;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("GlobalExceptionHandler RFC 7807 Unit Tests")
class GlobalExceptionHandlerTests {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest requestProxy;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();

        // Dynamic Proxy for HttpServletRequest to avoid mocking framework reflection hurdles
        requestProxy = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getRequestURI".equals(method.getName())) {
                        return "/orders";
                    }
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Should map MethodArgumentNotValidException to 400 Bad Request with field errors")
    void shouldHandleValidationException() throws Exception {
        Method method = OrderController.class.getMethod(
                "createOrder", CreateOrderRequestDto.class, String.class, String.class, UserPrincipal.class
        );
        MethodParameter param = new MethodParameter(method, 0);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                new CreateOrderRequestDto("", "USD"), "createOrderRequestDto"
        );
        bindingResult.addError(new FieldError("createOrderRequestDto", "shippingAddress", "Shipping address is mandatory"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ProblemDetail problem = exceptionHandler.handleValidationExceptions(ex, requestProxy);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Validation Error", problem.getTitle());
        assertNotNull(problem.getProperties());
        Map<?, ?> invalidParams = (Map<?, ?>) problem.getProperties().get("invalid_params");
        assertNotNull(invalidParams);
        assertEquals("Shipping address is mandatory", invalidParams.get("shippingAddress"));
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to 400 Bad Request")
    void shouldHandleBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid currency format");
        ProblemDetail problem = exceptionHandler.handleBadRequest(ex, requestProxy);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertEquals("Invalid currency format", problem.getDetail());
    }

    @Test
    @DisplayName("Should map UnauthorizedOrderAccessException to 403 Forbidden")
    void shouldHandleAccessDenied() {
        UnauthorizedOrderAccessException ex = new UnauthorizedOrderAccessException(UUID.randomUUID(), UUID.randomUUID());
        ProblemDetail problem = exceptionHandler.handleAccessDenied(ex, requestProxy);

        assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
        assertEquals("Forbidden", problem.getTitle());
    }

    @Test
    @DisplayName("Should map OrderNotFoundException and ProductNotFoundException to 404 Not Found")
    void shouldHandleNotFound() {
        OrderNotFoundException orderEx = new OrderNotFoundException(UUID.randomUUID());
        ProblemDetail orderProblem = exceptionHandler.handleNotFound(orderEx, requestProxy);
        assertEquals(HttpStatus.NOT_FOUND.value(), orderProblem.getStatus());

        ProductNotFoundException productEx = new ProductNotFoundException("prod-999");
        ProblemDetail productProblem = exceptionHandler.handleNotFound(productEx, requestProxy);
        assertEquals(HttpStatus.NOT_FOUND.value(), productProblem.getStatus());
    }

    @Test
    @DisplayName("Should map IdempotencyConflictException and InvalidOrderStateTransitionException to 409 Conflict")
    void shouldHandleConflict() {
        IdempotencyConflictException idempEx = new IdempotencyConflictException("key-123");
        ProblemDetail idempProblem = exceptionHandler.handleConflict(idempEx, requestProxy);
        assertEquals(HttpStatus.CONFLICT.value(), idempProblem.getStatus());

        InvalidOrderStateTransitionException stateEx = new InvalidOrderStateTransitionException(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        ProblemDetail stateProblem = exceptionHandler.handleConflict(stateEx, requestProxy);
        assertEquals(HttpStatus.CONFLICT.value(), stateProblem.getStatus());
    }

    @Test
    @DisplayName("Should map EmptyCartException, InsufficientStockException, and IdempotencyPayloadMismatchException to 422 Unprocessable Entity")
    void shouldHandleUnprocessableEntity() {
        EmptyCartException cartEx = new EmptyCartException("Shopping cart contains no items");
        ProblemDetail cartProblem = exceptionHandler.handleUnprocessableEntity(cartEx, requestProxy);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), cartProblem.getStatus());

        InsufficientStockException stockEx = new InsufficientStockException("prod-1", 5, 2);
        ProblemDetail stockProblem = exceptionHandler.handleUnprocessableEntity(stockEx, requestProxy);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), stockProblem.getStatus());

        IdempotencyPayloadMismatchException idempEx = new IdempotencyPayloadMismatchException("key-dup");
        ProblemDetail idempProblem = exceptionHandler.handleUnprocessableEntity(idempEx, requestProxy);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), idempProblem.getStatus());
    }

    @Test
    @DisplayName("Should map CartServiceException and CatalogServiceException to 502 Bad Gateway")
    void shouldHandleDownstreamExceptions() {
        CartServiceException ex = new CartServiceException("Shopping Cart Service unreachable", 503);
        ProblemDetail problem = exceptionHandler.handleDownstreamServiceException(ex, requestProxy);

        assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
        assertEquals("Bad Gateway", problem.getTitle());
    }

    @Test
    @DisplayName("Should map generic uncaught Exception to 500 Internal Server Error")
    void shouldHandleGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected database outage");
        ProblemDetail problem = exceptionHandler.handleGenericException(ex, requestProxy);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertEquals("Internal Server Error", problem.getTitle());
    }
}
