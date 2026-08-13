package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.security.JwtAuthenticationFilter;
import com.rtxnano.ecommerce.order.security.JwtTokenProvider;
import com.rtxnano.ecommerce.order.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("JWT Authentication Filter Unit Tests")
class JwtAuthenticationFilterTests {

    private static final String TEST_SECRET = "8d/vpFSCAFqeRdZD7W2ZbBUbvs9r3FajrfXlCDp4cTk=";

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should populate SecurityContext when valid Bearer token is provided")
    void shouldAuthenticateValidBearerToken() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        // Generate valid token
        String token = io.jsonwebtoken.Jwts.builder()
                .subject(userId.toString())
                .claim("email", "buyer@example.com")
                .claim("roles", java.util.List.of("CUSTOMER"))
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3600000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(TEST_SECRET.getBytes()))
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "SecurityContext should contain authentication");
        assertTrue(auth.isAuthenticated());
        assertTrue(auth.getPrincipal() instanceof UserPrincipal);

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals(userId, principal.getUserId());
        assertEquals("buyer@example.com", principal.getEmail());
        assertTrue(principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should ignore request without Authorization header and proceed anonymously")
    void shouldIgnoreMissingAuthHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear SecurityContext when invalid or expired token is provided")
    void shouldRejectInvalidBearerToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-tampered-token-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
