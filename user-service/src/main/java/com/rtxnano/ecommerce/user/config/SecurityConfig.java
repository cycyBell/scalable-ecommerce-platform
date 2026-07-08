package com.rtxnano.ecommerce.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// @Configuration tells Spring: "this class defines beans to register in
// the application context" — think of it as a factory that produces
// reusable, shared objects your other classes can request.
@Configuration
public class SecurityConfig {

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
}