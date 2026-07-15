package com.rtxnano.ecommerce.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// @Testcontainers activates JUnit 5 support for the @Container-annotated
// field below — it manages that container's lifecycle (start it before
// tests run, stop it afterward) automatically.
@Testcontainers
@SpringBootTest
class UserServiceApplicationTests {

    // @Container tells Testcontainers "this field represents a REAL
    // Docker container to manage for this test class." PostgreSQLContainer
    // is a Testcontainers-provided class specifically for spinning up a
    // real, temporary PostgreSQL instance, using the exact same
    // "postgres:16" image we've been running manually in Docker this
    // whole time — same real database engine, just disposable and
    // automatically managed.
    //
    // static means ONE container is shared across all test methods in
    // this class (started once, reused, torn down after the whole class
    // finishes) rather than restarting it before every single test
    // method, which would be needlessly slow.
    @Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer(
		DockerImageName.parse("postgres:16")
	);

    // @ServiceConnection is the key piece of glue: it tells Spring Boot
    // "automatically configure the datasource connection properties
    // (url, username, password) to point at THIS container" — we never
    // have to manually write spring.datasource.url=... for the test
    // context; Spring Boot inspects the running container and wires
    // itself up correctly, automatically, using whatever random port
    // Docker assigned it.
    //
    // This annotation goes on the container field itself.
    static {
        postgres.start();
    }

    @Test
    void contextLoads() {
        // If this test passes, it means: a fresh PostgreSQL container
        // started successfully, Flyway found and ran our migration
        // against it correctly, Hibernate validated our User entity
        // against the resulting schema, and the full Spring application
        // context booted without errors — all with ZERO manual setup,
        // no .env loading, no pre-existing Docker container needed.
    }
}
