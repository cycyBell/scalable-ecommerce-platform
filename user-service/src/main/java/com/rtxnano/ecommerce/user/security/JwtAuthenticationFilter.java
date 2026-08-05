package com.rtxnano.ecommerce.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter executing once per HTTP request to validate incoming Bearer JWT tokens.
 *
 * Key Educational Concept:
 * By extracting roles directly from the cryptographically verified JWT payload
 * claims ("roles"), this filter performs 100% stateless authorization without
 * making database calls to PostgreSQL. This unlocks linear scaling across
 * microservice instances under high concurrency.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path.equals("/auth/register") 
            || path.equals("/auth/login") 
            || path.equals("/actuator/health") 
            || path.equals("/auth/refresh") 
            || path.equals("/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. Check for Authorization header starting with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract raw token string
        final String jwt = authHeader.substring(7);

        try {
            final String email = jwtService.extractEmail(jwt);

            // 3. Authenticate statelessly if email is valid and no existing security context exists
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (jwtService.isTokenValid(jwt, email)) {
                    // Extract embedded roles directly from JWT payload claims (Zero-DB lookup)
                    List<String> roles = jwtService.extractRoles(jwt);

                    // Spring Security requires "ROLE_" prefix for hasRole('ADMIN') authorization checks
                    List<GrantedAuthority> authorities = roles.stream()
                            .map(role -> {
                                String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                                return new SimpleGrantedAuthority(roleName);
                            })
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Unverified or expired tokens leave request unauthenticated
            logger.warn("JWT authentication failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
