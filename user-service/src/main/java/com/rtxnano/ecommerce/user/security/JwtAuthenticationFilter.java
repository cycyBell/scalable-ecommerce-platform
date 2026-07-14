package com.rtxnano.ecommerce.user.security;

import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
// OncePerRequestFilter is a Spring base class that guarantees this
// filter's logic runs exactly ONCE per incoming HTTP request — even in
// edge cases involving internal request forwarding, where a naive filter
// might otherwise run twice. This is the standard base class for
// writing custom security filters.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

   // NEW dependency: we need to look up the user's actual roles from
    // the database, not just their email. The token itself only proves
    // WHO someone is — it doesn't carry their current roles, since
    // roles could change after a token was issued (e.g. an admin
    // demotes someone), and we want every request to reflect the
    // CURRENT, real roles, not stale ones baked into an old token.
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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


    // This method is the actual filter logic — it runs on every single
    // request that reaches our app, BEFORE it gets to any controller.
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        


        // Step 1: look for the Authorization header. By HTTP convention,
        // a bearer token is sent as: "Authorization: Bearer <token>"
        final String authHeader = request.getHeader("Authorization");

        // Step 2: if there's no Authorization header, or it doesn't
        // start with "Bearer ", this request isn't trying to
        // authenticate via JWT at all. We simply let it continue down
        // the filter chain AS-IS — meaning it'll be treated as
        // anonymous/unauthenticated. This is important: for public
        // endpoints like /auth/register, there IS no token, and that's
        // fine — SecurityFilterChain's permitAll() rule handles those
        // separately. We don't reject the request here; we just don't
        // attach any identity to it.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: extract just the token itself, stripping the "Bearer "
        // prefix (7 characters, including the space).
        final String jwt = authHeader.substring(7);

        // Step 4: extract the email from the token. If the token is
        // malformed, expired, or has an invalid signature, extractEmail
        // (via JwtService's internal parseSignedClaims call) will throw
        // an exception here. We deliberately let that exception
        // propagate for now — Spring's default error handling will turn
        // it into a response, and we'll refine this in Step 12.
        final String email = jwtService.extractEmail(jwt);

        // Step 5: only proceed if we got an email AND there's no
        // existing authentication already set for this request (this
        // second check avoids redundant work if something upstream
        // already authenticated this request some other way).
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Step 6: validate the token is genuinely valid for this
            // exact email (correct signature, not expired).
            if (jwtService.isTokenValid(jwt, email)) {


                // Step 7: THIS is the actual moment of authentication.
                // We build an "authentication token" object — Spring
                // Security's internal representation of "this request
                // has been verified to belong to this principal
                // (email)." We pass `null` for credentials (we don't
                // need to carry the password around anymore — the JWT
                // itself IS the proof), and an empty list of authorities
                // for now (we'll wire in actual roles/permissions when
                // we build RBAC in Step 8).

                 // NEW: look up the real user record to get their CURRENT
                // roles. We deliberately go back to the database here
                // rather than trusting anything from the token itself,
                // beyond identity (the email). This guarantees that if
                // an admin's role were revoked five minutes ago, this
                // exact request reflects that change immediately —
                // rather than an old token still granting stale admin
                // access until it naturally expires.
                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();

                    // Convert our Role enum values (CUSTOMER, ADMIN)
                    // into Spring Security's GrantedAuthority objects.
                    // Spring Security's convention is that role names
                    // are prefixed with "ROLE_" internally — this is
                    // what lets hasRole('ADMIN') work correctly, since
                    // hasRole() automatically adds that prefix when
                    // checking, so the authority itself must actually
                    // BE stored as "ROLE_ADMIN" to match.
                    List<GrantedAuthority> authorities = user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .collect(java.util.stream.Collectors.toList());

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // Step 9: whether or not authentication succeeded, let the
        // request continue to the next filter (and eventually, the
        // controller). If authentication failed or was never attempted,
        // later checks (like anyRequest().authenticated()) will
        // correctly reject the request with a 401/403 on their own —
        // this filter's job is only to ATTEMPT authentication, not to
        // decide whether the request is allowed to proceed.
        filterChain.doFilter(request, response);
    }
}
