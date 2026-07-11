package com.rtxnano.ecommerce.user.service;

import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.enums.Role;
import com.rtxnano.ecommerce.user.repository.UserRepository;
import com.rtxnano.ecommerce.user.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
// @Service marks this as a "business logic" bean — the layer that sits
// between the controller (which handles HTTP) and the repository
// (which handles the database). Controllers should stay thin (just
// receive requests and return responses); services hold the actual
// rules and decisions.
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Constructor injection: Spring sees this constructor and
    // automatically supplies both dependencies — the UserRepository
    // we built in Step 4, and the PasswordEncoder bean we just defined.
    // We never call "new UserService(...)" ourselves; Spring wires it
    // up for us at startup.
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(RegisterRequest request) {

        // Step 1: check for duplicates BEFORE doing any other work.
        // This uses the existsByEmail() method we defined in
        // UserRepository back in Step 4 — remember, Spring Data JPA
        // auto-generated the actual SQL for this from the method name.
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        // Step 2: build a real User entity from the incoming DTO.
        // Notice: we are explicitly deciding what gets copied over.
        // Nothing from the request can accidentally set fields like
        // `enabled` or `roles` to something the client chose — WE
        // decide those values here, not the incoming JSON.
        User user = new User();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());

        // Step 3: hash the password. This is the ONLY place the raw
        // password ever exists in memory — it's immediately transformed
        // into a hash and the original is never stored or logged
        // anywhere.
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        // Step 4: assign a default role. Every new signup becomes a
        // CUSTOMER; nobody can register themselves as ADMIN through
        // this public endpoint — admin promotion would be a separate,
        // protected operation we might add later.
        HashSet<Role> defaultRoles = new HashSet<>();
        defaultRoles.add(Role.CUSTOMER);
        user.setRoles(defaultRoles);

        // Step 5: persist it. This calls the save() method that comes
        // for free from JpaRepository<User, UUID> — Hibernate generates
        // the actual INSERT statement behind the scenes.
        return userRepository.save(user);
    }

    // Verifies login credentials and, if valid, returns a signed JWT
    // string. Returns just the token (a String) rather than the User
    // entity — the controller only needs to hand this back to the
    // client; it doesn't need the full user object at this point.
    public String login(LoginRequest request) {

        // Step 1: look up the user by email. If no user exists with
        // this email, throw the SAME generic error we'll throw for a
        // wrong password below. This is deliberate: using a DIFFERENT
        // message for "no such email" vs "wrong password" would let an
        // attacker discover which emails have real accounts on our
        // platform, just by observing which error comes back — a
        // vulnerability called "user enumeration." One generic message
        // for both cases closes that leak.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        // Step 2: check the password. passwordEncoder.matches() takes
        // the RAW password the user just typed (request.password()) and
        // the STORED BCrypt hash (user.getPasswordHash()), and checks
        // whether hashing the raw input produces a matching result. We
        // never decrypt the stored hash — BCrypt hashes can't be
        // reversed; we can only re-hash and compare.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Step 3: credentials are confirmed valid. Issue a fresh JWT,
        // identifying this user by their email (the "sub" claim, as
        // defined in JwtService.generateToken()).
        return jwtService.generateToken(user.getEmail());
    }
}