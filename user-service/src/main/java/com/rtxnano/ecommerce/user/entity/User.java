package com.rtxnano.ecommerce.user.entity;


import com.rtxnano.ecommerce.user.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// @Entity tells Hibernate: "this class maps to a database table."
// Without this annotation, User would just be a plain Java class with no
// connection to PostgreSQL at all.
@Entity
// @Table names the actual table "users" (plural, standard SQL convention)
// and defines a unique index on the email column, so the DATABASE ITSELF
// rejects duplicate signups — not just our Java validation code.
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email", unique = true)
})
// Lombok annotations: instead of manually writing getEmail(), setEmail(),
// getFirstName(), setFirstName(), etc. for every field below, Lombok
// generates all of that boilerplate automatically at compile time.
@Getter
@Setter
public class User {

    // @Id marks this field as the primary key of the table.
    // @GeneratedValue tells Hibernate to auto-generate a UUID for every
    // new user. We use UUID instead of a simple auto-incrementing number
    // so IDs aren't sequential/guessable across services (e.g. no one
    // can infer "/users/1, /users/2..." and enumerate all your users).
    @Id
    @GeneratedValue
    private UUID id;

    // The user's login identifier. nullable = false means the database
    // will reject any row where this is empty. unique = true is a second
    // layer of protection against duplicate emails, enforced at the
    // column level (in addition to the index above).
    @Column(nullable = false, unique = true)
    private String email;

    // IMPORTANT: this stores only the BCrypt HASH of the password, never
    // the real password itself. Named "passwordHash" (not "password") on
    // purpose, so nobody mistakes this for a reversible/plaintext field.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Split into first/last name (rather than one "name" field) so it's
    // easier to format things like shipping labels or formal greetings
    // later (e.g. "Dear Mr. Smith").
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    // Nullable because not every user will provide a phone number at
    // signup. Useful later for SMS notifications via Twilio.
    @Column(name = "phone_number")
    private String phoneNumber;

    // A user can have multiple roles (e.g. a user could be both CUSTOMER
    // and ADMIN), so this is modeled as a Set, not a single field.
    //
    // @ElementCollection + @CollectionTable tells Hibernate to create a
    // SEPARATE table called "user_roles" with a foreign key back to this
    // user's id. This is more query-friendly than cramming roles into a
    // single comma-separated string column (e.g. "CUSTOMER,ADMIN"), which
    // would be painful to search/filter later.
    //
    // @Enumerated(EnumType.STRING) stores the role as readable text
    // ("CUSTOMER") in the database instead of a number, so the raw table
    // data stays human-readable if you ever inspect it directly.
    //
    // fetch = FetchType.EAGER means roles are loaded immediately whenever
    // a User is loaded (reasonable here since roles are small and almost
    // always needed for authorization checks).
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

    // Lets us deactivate an account (e.g. banned user) WITHOUT deleting
    // their row — preserves order history, audit trails, etc.
    @Column(nullable = false)
    private boolean enabled = true;

    // Standard pattern for a future "verify your email" flow. Defaults
    // to false until the user clicks a verification link (not yet built).
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // updatable = false means this column is set once at creation and
    // Hibernate will never try to update it again, even if we forget and
    // accidentally reassign it somewhere in code.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // @PrePersist is a lifecycle hook: this method runs automatically
    // right before this entity is FIRST saved to the database. We use it
    // to auto-stamp createdAt/updatedAt so we never have to remember to
    // set them manually in our service/controller code.
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    // @PreUpdate runs automatically right before any EXISTING row is
    // updated, keeping updatedAt accurate without any manual bookkeeping.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
