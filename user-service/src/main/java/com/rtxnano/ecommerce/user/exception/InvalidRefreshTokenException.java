package com.rtxnano.ecommerce.user.exception;

// Used by the /auth/refresh endpoint when the provided refresh token
// doesn't exist in Redis (expired, revoked, or never valid). Distinct
// from InvalidCredentialsException because this isn't about a
// email/password pair — it's specifically about token validity.
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}