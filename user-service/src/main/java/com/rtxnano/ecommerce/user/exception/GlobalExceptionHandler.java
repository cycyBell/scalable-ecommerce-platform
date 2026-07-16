package com.rtxnano.ecommerce.user.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// @RestControllerAdvice is Spring's mechanism for a GLOBAL exception
// handler — rather than writing try/catch blocks in every single
// controller method (which we've deliberately NOT been doing this whole
// time, precisely because this step was coming), this ONE class
// intercepts exceptions thrown from ANY @RestController in the
// application and converts them into clean, consistent HTTP responses.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // @ExceptionHandler tells Spring: "whenever a method anywhere in a
    // controller throws THIS exact exception type (or a subclass of
    // it), route it here instead of letting it crash into a generic
    // 500." Spring's dispatcher watches for exceptions bubbling up from
    // controller methods and matches them against these handlers.
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        // 409 Conflict is the semantically correct status for "the
        // request is valid, but conflicts with existing state" — exactly
        // what a duplicate email registration is.
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        // 401 Unauthorized: "you have not successfully authenticated."
        // This is the semantically correct status for a failed login —
        // distinct from 403 (authenticated, but not permitted), which
        // is reserved for RBAC-style rejections like our /users/all
        // ADMIN check.
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        // 404 Not Found: genuinely the right status here, since this
        // scenario means "a specific resource (this user) doesn't
        // exist" — different in meaning from 401 (bad credentials).
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // This handler is DIFFERENT from the others — it doesn't return our
    // simple ErrorResponse. @Valid validation failures (like a password
    // under 8 characters) throw MethodArgumentNotValidException, which
    // can contain MULTIPLE field errors at once (e.g. both email AND
    // password invalid in the same request). We build a richer response
    // that lists every failing field, rather than just one message.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        // LinkedHashMap preserves insertion order, so field errors
        // appear in a predictable, readable order in the response —
        // purely a readability nicety, not a functional requirement.
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // getFieldErrors() gives us every individual validation failure
        // from the request (e.g. one entry for "password", one for
        // "email", if both failed simultaneously).
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Validation failed");
        body.put("path", request.getRequestURI());
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // The FINAL, catch-all safety net. Any exception not specifically
    // handled above (a genuine bug, an unexpected null pointer, etc.)
    // lands here instead of leaking a raw Java stack trace to the
    // client — which would both look unprofessional AND potentially
    // expose internal implementation details to whoever's calling the API.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}