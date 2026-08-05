package com.rtxnano.ecommerce.user.service;

import com.rtxnano.ecommerce.user.dto.AuthTokens;
import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.dto.UserProfileResponse;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.enums.Role;
import com.rtxnano.ecommerce.user.exception.EmailAlreadyExistsException;
import com.rtxnano.ecommerce.user.exception.InvalidCredentialsException;
import com.rtxnano.ecommerce.user.exception.RateLimitExceededException;
import com.rtxnano.ecommerce.user.exception.UserNotFoundException;
import com.rtxnano.ecommerce.user.repository.UserRepository;
import com.rtxnano.ecommerce.user.security.JwtService;
import com.rtxnano.ecommerce.user.security.LoginRateLimiterService;
import com.rtxnano.ecommerce.user.security.RefreshTokenService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service encapsulating core user management, registration, and authentication business logic.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiterService rateLimiterService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginRateLimiterService rateLimiterService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiterService = rateLimiterService;
    }

    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserProfileResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        HashSet<Role> defaultRoles = new HashSet<>();
        defaultRoles.add(Role.CUSTOMER);
        user.setRoles(defaultRoles);

        return userRepository.save(user);
    }

    public AuthTokens login(LoginRequest request) {
        String email = request.email();

        // 1. Check rate limit before executing password verification
        if (rateLimiterService.isRateLimited(email)) {
            throw new RateLimitExceededException("Too many failed login attempts. Please try again in 15 minutes.");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        // 2. Validate password match using BCrypt
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiterService.incrementFailedAttempts(email);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // 3. Clear rate limiter counter on successful login
        rateLimiterService.resetAttempts(email);

        // 4. Issue JWT access token with embedded roles and create refresh token
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRoles());
        String refreshToken = refreshTokenService.createRefreshToken(user.getEmail());

        return new AuthTokens(accessToken, refreshToken);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}