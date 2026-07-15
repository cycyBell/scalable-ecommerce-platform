package com.rtxnano.ecommerce.user.config;

import com.rtxnano.ecommerce.user.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// @Configuration tells Spring: "this class defines beans to register in
// the application context" — think of it as a factory that produces
// reusable, shared objects your other classes can request.
@Configuration
public class SecurityConfig {


    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // We now need our custom filter injected here, so we can insert it
    // into the chain below.
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // @Bean tells Spring: "call this method once at startup, and make
    // the object it returns available anywhere else in the app via
    // dependency injection." Any class can now simply declare
    // `private final PasswordEncoder passwordEncoder;` in its
    // constructor, and Spring will automatically hand it this exact
    // instance — no manual wiring needed.
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is a purpose-built password-hashing algorithm — NOT a
        // general encryption algorithm. It's deliberately slow (by
        // design) to make brute-force password-guessing attacks
        // computationally expensive, and it automatically handles
        // "salting" (adding random data to each password before
        // hashing) so two users with the same password produce
        // completely different hash values in the database.
        return new BCryptPasswordEncoder();
    }

    // This bean is where we define our actual security rules. Spring
    // Security will find this bean at startup and use it INSTEAD OF its
    // default "lock everything down" behavior.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF (Cross-Site Request Forgery) protection is designed
            // for traditional, cookie-based, session-driven web apps —
            // where a browser automatically attaches cookies to every
            // request. We're building a stateless, JWT-based REST API,
            // where the client must explicitly attach a token to every
            // request instead. CSRF protection doesn't apply to this
            // architecture, so we turn it off explicitly rather than
            // leave it fighting against a model it wasn't designed for.
            .csrf(csrf -> csrf.disable())

            // This is the actual heart of the configuration: defining
            // which URLs need authentication and which don't.
            .authorizeHttpRequests(auth -> auth
                // permitAll() means: "let any request through to these
                // paths, authenticated or not." This makes sense here —
                // a brand-new user registering, or an existing user
                // logging in, cannot possibly already hold a valid JWT
                // yet. These are the literal entry points INTO the
                // authenticated part of the system.
                .requestMatchers("/auth/register", "/auth/login","/auth/refresh","/auth/logout").permitAll()
                .requestMatchers("/error").permitAll()

                // We'll also allow the actuator health endpoint publicly
                // — remember, this is what Kubernetes/monitoring tools
                // will hit to check "is this service alive?" It
                // shouldn't require a login to check basic health.
                .requestMatchers("/actuator/health").permitAll()

                // anyRequest().authenticated() is the catch-all: EVERY
                // other endpoint we build from here on (profile,
                // orders, etc.) requires a valid, authenticated request
                // by default. This is a deliberate "secure by default"
                // choice — we explicitly opt individual endpoints INTO
                // being public, rather than opting endpoints OUT of
                // security one by one (which is much easier to get
                // wrong and accidentally leave something exposed).
                .anyRequest().authenticated()
            )

            // This tells Spring Security: "do not create or rely on an
            // HTTP session to remember who's logged in between
            // requests." Each request must independently prove who it
            // is — which is exactly what a JWT does. This is the
            // concrete implementation of the "stateless" architecture
            // point we discussed: no shared server-side memory of
            // "who's logged in," which is essential in a microservices
            // system where multiple independent services need to verify
            // identity without depending on one one shared session
            // store.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // THIS is the new piece: we insert our custom filter into
            // Spring Security's filter chain, specifically BEFORE the
            // built-in UsernamePasswordAuthenticationFilter (the
            // standard filter Spring Security normally uses for
            // traditional form-login authentication, which we're not
            // using, but the position still matters as a reference
            // point in the chain). This ensures our JWT check runs
            // early, before other authentication mechanisms get a
            // chance to run.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        // NOTE: we have NOT yet added the actual JWT verification filter
        // here. Right now, this configuration correctly BLOCKS
        // unauthenticated requests to protected endpoints, but we don't
        // have any endpoints that require a real logged-in user yet
        // (that starts with Step 9, /users/me). The JWT filter itself —
        // which reads the Authorization header, validates the token,
        // and tells Spring Security "this request is authenticated as
        // user X" — gets added in Step 6/7, right after we build login
        // and JWT issuance. Right now, this bean's only real, testable
        // effect is: it finally lets /auth/register through.

        return http.build();
    }
}