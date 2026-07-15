package com.rtxnano.ecommerce.user.dto;

// Add this near the top of UserService.java, or as its own file in dto/
// if you'd rather keep UserService focused purely on logic. Given it's
// only used as this method's return value, nesting it here is
// reasonable for now — same "start simple, promote later if reused"
// reasoning we've used before.
public record AuthTokens(String accessToken, String refreshToken) {}
