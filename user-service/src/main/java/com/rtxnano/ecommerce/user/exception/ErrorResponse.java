package com.rtxnano.ecommerce.user.exception;

import java.time.Instant;

// A single, consistent shape for EVERY error response this service ever
// returns — whether it's a validation failure, a duplicate email, bad
// credentials, or an unexpected server error. Consistency here matters
// genuinely: any client consuming this API (a frontend, another
// microservice, Postman) can rely on error responses always looking the
// same shape, rather than guessing field names per endpoint.
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}