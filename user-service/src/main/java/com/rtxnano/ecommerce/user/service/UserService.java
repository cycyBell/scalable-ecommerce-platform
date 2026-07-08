package com.rtxnano.ecommerce.user.service;

import com.rtxnano.ecommerce.user.dto.RegisterRequest;
import com.rtxnano.ecommerce.user.entity.User;
import com.rtxnano.ecommerce.user.enums.Role;
import com.rtxnano.ecommerce.user.repository.UserRepository;
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

    // Constructor injection: Spring sees this constructor and
    // automatically supplies both dependencies — the UserRepository
    // we built in Step 4, and the PasswordEncoder bean we just defined.
    // We never call "new UserService(...)" ourselves; Spring wires it
    // up for us at startup.
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
}