package com.rtxnano.ecommerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A record is Java's built-in way to model an immutable data carrier.
// The compiler auto-generates the constructor, accessors (email(),
// password(), etc.), equals(), hashCode(), and toString() — no Lombok
// needed. Nothing can mutate this object after it's created, which is
// exactly the property we want for "data the client sent us."
public record RegisterRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    @NotBlank(message = "First name is required")
    String firstName,

    @NotBlank(message = "Last name is required")
    String lastName,

    // No @NotBlank — this one is optional at signup.
    String phoneNumber
) {}