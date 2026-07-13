package com.rtxnano.ecommerce.user.controller;

import com.rtxnano.ecommerce.user.dto.UserProfileResponse;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    // Handles GET /users/me. Notice there's no @PathVariable or
    // @RequestParam for "which user" — that's deliberate. The identity
    // comes entirely from the authenticated request itself, via the
    // Authentication parameter below, not from anything the client
    // explicitly specifies. This is exactly why it's called "/me" and
    // not "/users/{id}" — a user can only ever see their OWN profile
    // through this endpoint.
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(Authentication authentication) {

        // THIS is where everything we built in JwtAuthenticationFilter
        // finally gets used. Remember: that filter called
        // SecurityContextHolder.getContext().setAuthentication(authToken)
        // for any request carrying a valid JWT. Spring automatically
        // makes that same Authentication object available here, simply
        // by declaring it as a method parameter — Spring recognizes the
        // Authentication type and injects the current request's
        // security context into it for us.
        //
        // authentication.getName() returns whatever we set as the
        // "principal" when we built the UsernamePasswordAuthenticationToken
        // back in the filter — which was the user's email.
        String email = authentication.getName();

        User user = userService.getByEmail(email);

        return ResponseEntity.ok(UserProfileResponse.fromEntity(user));
    }
}
