package com.rtxnano.ecommerce.order.exception;

import com.rtxnano.ecommerce.order.client.exception.CartServiceException;
import com.rtxnano.ecommerce.order.client.exception.CatalogServiceException;
import com.rtxnano.ecommerce.order.client.exception.EmptyCartException;
import com.rtxnano.ecommerce.order.client.exception.InsufficientStockException;
import com.rtxnano.ecommerce.order.client.exception.ProductNotFoundException;
import com.rtxnano.ecommerce.order.domain.exception.InvalidOrderStateTransitionException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyConflictException;
import com.rtxnano.ecommerce.order.idempotency.exception.IdempotencyPayloadMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ==============================================================================
 * ADVICE: GlobalExceptionHandler
 * ==============================================================================
 * Standardizes API error responses across the microservice according to the
 * RFC 7807 (Problem Details for HTTP APIs) specification.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.ecommerce.rtxnano.com/errors/" + status.value()));
        problem.setProperty("timestamp", Instant.now());
        if (request != null) {
            problem.setProperty("path", request.getRequestURI());
        }
        return problem;
    }

    /**
     * Handles Bean Validation failures (@Valid / @NotNull / @NotBlank).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Request validation failed for one or more fields",
                request
        );
        problem.setProperty("invalid_params", errors);
        log.warn("Validation error on {}: {}", request.getRequestURI(), errors);
        return problem;
    }

    /**
     * Handles illegal client arguments & unparseable JSON payloads.
     */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        log.warn("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
    }

    /**
     * Handles unauthorized access attempts to another customer's order.
     */
    @ExceptionHandler({UnauthorizedOrderAccessException.class, AccessDeniedException.class})
    public ProblemDetail handleAccessDenied(Exception ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request);
    }

    /**
     * Handles resource not found exceptions (Order or Product).
     */
    @ExceptionHandler({OrderNotFoundException.class, ProductNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex, HttpServletRequest request) {
        log.warn("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    /**
     * Handles concurrency / state machine / idempotency conflict exceptions.
     */
    @ExceptionHandler({IdempotencyConflictException.class, InvalidOrderStateTransitionException.class})
    public ProblemDetail handleConflict(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflict detected on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    /**
     * Handles unprocessable business logic exceptions (empty cart, out of stock, payload hash mismatch).
     */
    @ExceptionHandler({EmptyCartException.class, InsufficientStockException.class, IdempotencyPayloadMismatchException.class})
    public ProblemDetail handleUnprocessableEntity(RuntimeException ex, HttpServletRequest request) {
        log.warn("Unprocessable entity on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage(), request);
    }

    /**
     * Handles downstream microservice communication failures (Cart Service / Catalog Service).
     */
    @ExceptionHandler({CartServiceException.class, CatalogServiceException.class})
    public ProblemDetail handleDownstreamServiceException(RuntimeException ex, HttpServletRequest request) {
        log.error("Downstream microservice error on {}: {}", request.getRequestURI(), ex.getMessage());
        return createProblemDetail(HttpStatus.BAD_GATEWAY, "Bad Gateway", ex.getMessage(), request);
    }

    /**
     * Catch-all fallback for uncaught system exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred while processing your request",
                request
        );
    }
}
