package com.rtxnano.ecommerce.order.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ==============================================================================
 * PRINCIPAL: UserPrincipal
 * ==============================================================================
 * Encapsulates the authenticated user's identity (UUID userId, email, roles)
 * extracted directly from the stateless JWT without database lookups.
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final Set<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID userId, String email, Set<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.authorities = authorities != null ? Collections.unmodifiableSet(authorities) : Collections.emptySet();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public boolean hasRole(String role) {
        String expectedAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(expectedAuthority));
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return ""; // Stateless JWT authentication does not maintain session passwords
    }

    @Override
    public String getUsername() {
        return email != null ? email : userId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", authorities=" + authorities +
                '}';
    }
}
