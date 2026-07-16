package com.rtxnano.ecommerce.user.exception;

// Used specifically by getByEmail() (called from /users/me) — a
// genuinely different scenario from login failure: this represents "a
// valid, authenticated JWT pointed to a user that no longer exists,"
// which is an unusual, almost-shouldn't-happen case, not a login
// rejection. Worth its own type rather than reusing InvalidCredentialsException,
// since the MEANING is different even if it feels superficially similar.
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}