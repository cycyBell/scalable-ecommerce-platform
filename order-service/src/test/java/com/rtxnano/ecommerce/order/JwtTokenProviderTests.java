package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.security.JwtTokenProvider;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JWT Token Provider Unit Tests")
class JwtTokenProviderTests {

    private static final String TEST_SECRET = "8d/vpFSCAFqeRdZD7W2ZbBUbvs9r3FajrfXlCDp4cTk=";
    private static final String OTHER_SECRET = "different_secret_key_for_testing_tampered_tokens_123456=";

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET);
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String createTestToken(String subject, String email, String userId, List<String> roles, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(testKey);

        if (email != null) {
            builder.claim("email", email);
        }
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (roles != null) {
            builder.claim("roles", roles);
        }

        return builder.compact();
    }

    @Test
    @DisplayName("Should validate a properly signed, non-expired JWT token")
    void shouldValidateCorrectToken() {
        UUID userId = UUID.randomUUID();
        String token = createTestToken(userId.toString(), "customer@example.com", userId.toString(), List.of("CUSTOMER"), 3600000);

        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(userId, jwtTokenProvider.extractUserId(token));
        assertEquals("customer@example.com", jwtTokenProvider.extractEmail(token));

        Set<String> authorities = jwtTokenProvider.extractAuthorities(token).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("Should reject an expired JWT token")
    void shouldRejectExpiredToken() {
        UUID userId = UUID.randomUUID();
        String expiredToken = createTestToken(userId.toString(), "user@example.com", userId.toString(), List.of("CUSTOMER"), -5000);

        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }

    @Test
    @DisplayName("Should reject a token signed with a different key (signature tampering)")
    void shouldRejectTamperedToken() {
        SecretKey untrustedKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
                .subject("tampered-user")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(untrustedKey)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should build valid Authentication and UserPrincipal from JWT")
    void shouldBuildAuthenticationPrincipal() {
        UUID userId = UUID.randomUUID();
        String token = createTestToken(userId.toString(), "admin@example.com", userId.toString(), List.of("ADMIN", "CUSTOMER"), 3600000);

        Authentication auth = jwtTokenProvider.getAuthentication(token);
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());

        assertTrue(auth.getPrincipal() instanceof UserPrincipal);
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        assertEquals(userId, principal.getUserId());
        assertEquals("admin@example.com", principal.getEmail());
        assertTrue(principal.isAdmin());
        assertTrue(principal.hasRole("CUSTOMER"));
        assertTrue(principal.hasRole("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("Should gracefully handle email-based subject when userId claim is missing")
    void shouldDeriveDeterministicUuidFromEmailSubject() {
        String email = "john.doe@example.com";
        String token = createTestToken(email, email, null, List.of("CUSTOMER"), 3600000);

        assertTrue(jwtTokenProvider.validateToken(token));
        UUID derivedId = jwtTokenProvider.extractUserId(token);
        assertNotNull(derivedId);
        assertEquals(UUID.nameUUIDFromBytes(("user:" + email).getBytes(StandardCharsets.UTF_8)), derivedId);
    }
}
