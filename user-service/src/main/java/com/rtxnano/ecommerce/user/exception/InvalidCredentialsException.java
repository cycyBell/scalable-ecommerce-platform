package com.rtxnano.ecommerce.user.exception;

// A dedicated exception for login failures — covers BOTH "no such user"
// and "wrong password" scenarios. Keeping this as ONE exception type
// (rather than two separate ones) is deliberate: remember the user-
// enumeration protection we designed earlier — both failure cases must
// look identical to the outside world. Using a single exception type
// for both reinforces that at the code level, not just in the message text.
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}