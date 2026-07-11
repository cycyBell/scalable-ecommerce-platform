package com.rtxnano.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;

// Simpler than RegisterRequest — login only needs credentials, nothing
// else. Still a record: immutable, no boilerplate needed.
public record LoginRequest(

    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}