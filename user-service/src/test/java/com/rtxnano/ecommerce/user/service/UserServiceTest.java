package com.rtxnano.ecommerce.user.service;

import com.rtxnano.ecommerce.user.dto.AuthTokens;
import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.enums.Role;
import com.rtxnano.ecommerce.user.repository.UserRepository;
import com.rtxnano.ecommerce.user.security.JwtService;
import com.rtxnano.ecommerce.user.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) tells JUnit 5 to activate Mockito's
// machinery for this test class — specifically, to process the @Mock
// and @InjectMocks annotations below. Without this, those annotations
// would just be inert; nothing would actually create the fake objects.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // @Mock creates a FAKE UserRepository — not a real one connected to
    // any database. By default, every method on a mock does nothing and
    // returns null/false/empty — we explicitly program the specific
    // behaviors we need for each test, below.
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    // @InjectMocks creates a REAL UserService instance, but automatically
    // wires in all the @Mock objects above as its constructor
    // dependencies. This is the actual class under test — everything
    // else in this file exists to support testing THIS one class in
    // isolation.
    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    // @BeforeEach runs before EVERY single test method in this class,
    // giving each test a fresh, known starting point rather than
    // accidentally sharing state between tests.
    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
                "alice@example.com",
                "password123",
                "Alice",
                "Smith",
                null
        );
    }

    @Test
    void register_shouldSaveNewUser_whenEmailIsNotTaken() {
        // ARRANGE: program our fake objects' behavior for this specific
        // scenario. "when(...).thenReturn(...)" tells Mockito: "when
        // this exact method is called during this test, pretend it
        // returned this value" — no real database or hashing happens.
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        // We also need save() to return SOMETHING, since register()
        // returns whatever save() gives back. We use an "answer" here
        // to simulate the repository handing back the exact same User
        // object it was given — a realistic approximation of what a
        // real save() call does.
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT: call the actual method under test.
        User result = userService.register(registerRequest);

        // ASSERT: check that UserService did the right things.
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(result.getRoles()).containsExactly(Role.CUSTOMER);

        // VERIFY: confirm specific methods were actually CALLED, not
        // just check the end result. This is a different kind of
        // assertion — it inspects BEHAVIOR (did we call save() exactly
        // once?), not just the returned value.
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_shouldThrowException_whenEmailAlreadyExists() {
        // ARRANGE: this time, simulate the email already being taken.
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // ACT + ASSERT combined: assertThatThrownBy runs the given code
        // and expects it to throw. This directly tests the duplicate-
        // email rejection logic we built and manually tested with
        // curl/Postman much earlier — now it's automated, permanent,
        // and runs in milliseconds instead of requiring a live server.
        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already in use");

        // Critically: confirm save() was NEVER called. This proves we
        // correctly short-circuited before doing any real work — not
        // just that an exception happened to get thrown somewhere.
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldReturnTokens_whenCredentialsAreValid() {
        // ARRANGE: build a fake existing User to represent "what's
        // already in the database" for this scenario.
        User existingUser = new User();
        existingUser.setEmail("alice@example.com");
        existingUser.setPasswordHash("hashed-password");
        existingUser.setRoles(Set.of(Role.CUSTOMER));

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken("alice@example.com")).thenReturn("fake-access-token");
        when(refreshTokenService.createRefreshToken("alice@example.com")).thenReturn("fake-refresh-token");

        // ACT
        AuthTokens tokens = userService.login(loginRequest);

        // ASSERT
        assertThat(tokens.accessToken()).isEqualTo("fake-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("fake-refresh-token");
    }

    @Test
    void login_shouldThrowException_whenPasswordIsWrong() {
        User existingUser = new User();
        existingUser.setEmail("alice@example.com");
        existingUser.setPasswordHash("hashed-password");

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "wrongpassword");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongpassword", "hashed-password")).thenReturn(false);

        // This directly tests the user-enumeration protection we
        // designed earlier — same exception, same message, whether the
        // password is wrong or the user doesn't exist at all (tested
        // in the next method below).
        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_shouldThrowException_whenUserDoesNotExist() {
        LoginRequest loginRequest = new LoginRequest("nobody@example.com", "whatever123");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email or password");
    }
}
