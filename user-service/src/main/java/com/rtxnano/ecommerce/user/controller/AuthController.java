package com.rtxnano.ecommerce.user.controller;

import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
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

    // Constructor injection again — same pattern as UserService. Spring
    // sees this constructor, finds a UserService bean already registered
    // (thanks to @Service), and supplies it automatically.
    public AuthController(UserService userService) {
        this.userService = userService;
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

    // A tiny inline record just to shape the response safely. Since it's
    // only used here, nested is fine for now — if it grows or gets
    // reused elsewhere, we'd move it to its own file in dto/.
    private record RegisterResponse(String id, String email) {}
}