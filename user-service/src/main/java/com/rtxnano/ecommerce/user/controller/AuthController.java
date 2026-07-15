package com.rtxnano.ecommerce.user.controller;

import com.rtxnano.ecommerce.user.dto.AuthTokens;
import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RefreshRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.security.JwtService;
import com.rtxnano.ecommerce.user.security.RefreshTokenService;
import com.rtxnano.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController marks this class as a web controller where every method's
// return value is automatically serialized into the HTTP response body
// (as JSON), rather than being treated as a view name to render.
@RestController
// @RequestMapping sets a shared URL prefix for every endpoint in this
// class — so this method below actually becomes reachable at "/auth/register".
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    // Constructor injection again — same pattern as UserService. Spring
    // sees this constructor, finds a UserService bean already registered
    // (thanks to @Service), and supplies it automatically.
    public AuthController(UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    // @PostMapping("/register") + class-level @RequestMapping("/auth")
    // together mean: this method handles HTTP POST requests to
    // "/auth/register".
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        // @Valid tells Spring: "before running this method's body at all,
        // check the incoming RegisterRequest against every validation
        // annotation we wrote earlier (@NotBlank, @Email, @Size)." If
        // any of those fail, Spring automatically returns a 400 Bad
        // Request WITHOUT ever executing this method body — we don't
        // need to write manual "if invalid, return error" checks here.

        // @RequestBody tells Spring: "take the raw JSON from the HTTP
        // request body and automatically convert it into a
        // RegisterRequest object" (using Jackson, which Spring Boot
        // wires up for you automatically via the Web dependency).

        User savedUser = userService.register(request);

        // We deliberately do NOT return the full User entity here —
        // that would leak the passwordHash field straight into the API
        // response, which is a real security mistake. For now, we
        // return a minimal confirmation. In a later step, we could
        // build a dedicated UserResponse DTO to shape this more
        // formally — but returning just the ID and email is a safe,
        // honest minimum for now.
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(savedUser.getId().toString(), savedUser.getEmail()));
    }

   // Handles POST /auth/login. @Valid triggers LoginRequest's
    // @NotBlank checks before this method body even runs; if they fail,
    // Spring automatically returns 400 Bad Request without us writing
    // any manual checks.
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        // UserService.login() either returns a valid JWT string, or
        // throws IllegalArgumentException("Invalid email or password")
        // — which, right now, with no custom exception handling yet,
        // will surface as a generic 500. This will become a proper 401
        // Unauthorized once we build the Global Exception Handler
        // (Step 12), same as the duplicate-email case in register().
        AuthTokens authTokens = userService.login(request);

        return ResponseEntity.ok(new LoginResponse(authTokens.accessToken(), authTokens.refreshToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {

        // Look up whose refresh token this is. If it doesn't exist in Redis
        // (expired, revoked, or never existed), this returns null.
        String email = refreshTokenService.getEmailFromRefreshToken(request.refreshToken());

        if (email == null) {
            // Same principle as login's generic error message: don't leak
            // WHY it failed (expired vs. revoked vs. never existed) — just
            // that it's invalid.
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // Issue a brand-new access token. Notice we do NOT issue a new
        // refresh token here in this simple version — the same refresh
        // token can be reused until it naturally expires or is revoked.
        // (A more advanced pattern, "refresh token rotation," issues a NEW
        // refresh token on every use and invalidates the old one — worth
        // knowing exists, but we're keeping this simpler for now.)
        String newAccessToken = jwtService.generateToken(email);

        return ResponseEntity.ok(new RefreshResponse(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest request) {

        // Revoking is simple: just delete the key from Redis. Once deleted,
        // this refresh token can never be exchanged for a new access token
        // again — immediately, regardless of its original expiration.
        refreshTokenService.revokeRefreshToken(request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    private record RefreshResponse(String accessToken) {}

    // Tiny, single-use response shapes. Kept private and nested since
    // nothing else in the codebase needs them yet — if that changes,
    // we'd promote them to their own files in dto/, same reasoning we
    // used earlier when discussing the UserRole entity question.
    private record RegisterResponse(String id, String email) {}
    private record LoginResponse(String accessToken, String refreshToken) {}
}