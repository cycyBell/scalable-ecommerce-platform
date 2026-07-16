package com.rtxnano.ecommerce.user.exception;

// A dedicated exception type for exactly one scenario: someone tried to
// register with an email that's already taken. Extending RuntimeException
// (not the checked Exception) means callers aren't FORCED to declare
// "throws EmailAlreadyExistsException" everywhere or wrap every call in
// try/catch — consistent with how the rest of Spring's own exceptions
// work (e.g. IllegalArgumentException itself is unchecked).
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}