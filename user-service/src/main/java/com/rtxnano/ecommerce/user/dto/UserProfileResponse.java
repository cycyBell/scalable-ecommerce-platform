package com.rtxnano.ecommerce.user.dto;

import com.rtxnano.ecommerce.user.entity.User;

import java.util.Set;
import java.util.stream.Collectors;

// A record shaping exactly what we're willing to expose about a user's
// own profile. Notice passwordHash is nowhere in this list — that
// field should NEVER leave the backend via any API response, ever.
public record UserProfileResponse(
    String id,
    String email,
    String firstName,
    String lastName,
    String phoneNumber,
    Set<String> roles
) {
    // A small static factory method: given a real User entity (loaded
    // from the database), build the safe, public-facing version of it.
    // Centralizing this conversion here means every endpoint that needs
    // to return a user's profile does it the same, safe way — rather
    // than each controller manually picking fields and risking mistakes.
    public static UserProfileResponse fromEntity(User user) {
        return new UserProfileResponse(
            user.getId().toString(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhoneNumber(),
            user.getRoles().stream()
                .map(Enum::name)          // converts Role.CUSTOMER -> "CUSTOMER"
                .collect(Collectors.toSet())
        );
    }
}