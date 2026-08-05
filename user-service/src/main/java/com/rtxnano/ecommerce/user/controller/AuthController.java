package com.rtxnano.ecommerce.user.controller;

import com.rtxnano.ecommerce.user.dto.AuthTokens;
import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RefreshRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.exception.InvalidRefreshTokenException;
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

/**
 * Controller handling user authentication, registration, token rotation, and logout routes.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User savedUser = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponse(savedUser.getId().toString(), savedUser.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        AuthTokens authTokens = userService.login(request);
        return ResponseEntity.ok(new LoginResponse(authTokens.accessToken(), authTokens.refreshToken()));
    }

    /**
     * Refresh Token Rotation:
     * Validates incoming refresh token, invalidates it, issues a brand-new refresh token
     * along with a fresh JWT access token containing current user roles.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        String email = refreshTokenService.getEmailFromRefreshToken(request.refreshToken());

        if (email == null) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh token");
        }

        User user = userService.getByEmail(email);

        // Perform Refresh Token Rotation
        String newRefreshToken = refreshTokenService.rotateRefreshToken(request.refreshToken());
        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getRoles());

        return ResponseEntity.ok(new LoginResponse(newAccessToken, newRefreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private record RegisterResponse(String id, String email) {}
    private record LoginResponse(String accessToken, String refreshToken) {}
}