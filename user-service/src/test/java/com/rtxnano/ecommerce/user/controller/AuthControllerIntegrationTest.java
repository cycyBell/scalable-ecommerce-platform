package com.rtxnano.ecommerce.user.controller;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;

import com.redis.testcontainers.RedisContainer;
import com.rtxnano.ecommerce.user.dto.LoginRequest;
import com.rtxnano.ecommerce.user.dto.RegisterRequest;



import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

// webEnvironment = RANDOM_PORT actually starts a real embedded Tomcat
// server on a random free port, and gives us a real HTTP client
// (TestRestTemplate) to send genuine HTTP requests against it — this is
// meaningfully different from the unit tests, which never touched HTTP,
// filters, or a real server at all.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
		DockerImageName.parse("postgres:16")
	);

    @Container
    private static final RedisContainer REDIS_CONTAINER = 
        new RedisContainer(DockerImageName.parse("redis:7.2-alpine"));

    
    @DynamicPropertySource
    static void registerRedisProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }
    // Spring Boot automatically injects the actual random port this
    // test's embedded server started on.
    @LocalServerPort
    private int port;

    // TestRestTemplate is Spring's dedicated HTTP client for exactly
    // this kind of integration test — genuinely sends real HTTP
    // requests, just like Postman/curl did throughout our manual
    // testing, except now it's code, repeatable, and automated.
    @Autowired
    private TestRestTemplate restTemplate;


    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void register_thenLogin_shouldSucceed() {
        // ARRANGE
        RegisterRequest registerRequest = new RegisterRequest(
                "integrationtest@example.com",
                "password123",
                "Integration",
                "Test",
                null
        );

        // ACT: real HTTP POST to the real, running endpoint.
        ResponseEntity<Object> registerResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                registerRequest,
                Object.class
        );

        // ASSERT: this exercises the ENTIRE chain for real — validation,
        // UserService, UserRepository, actual Flyway-migrated database,
        // BCrypt hashing, the whole thing — not a mock in sight.
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Now actually log in with the same credentials, proving
        // registration and login work together correctly, end to end.
        LoginRequest loginRequest = new LoginRequest("integrationtest@example.com", "password123");

        ResponseEntity<Object> loginResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/login",
                loginRequest,
                Object.class
        );

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
    }

    @Test
    void register_shouldReject_whenEmailAlreadyRegistered() {
        RegisterRequest registerRequest = new RegisterRequest(
                "duplicate@example.com",
                "password123",
                "Dup",
                "User",
                null
        );

        // Register once — should succeed.
        restTemplate.postForEntity(baseUrl() + "/auth/register", registerRequest, Object.class);

        // Register again with the SAME email — should fail. This is a
        // genuine, automated version of the exact duplicate-email test
        // we ran manually with Postman several sessions ago.
        ResponseEntity<Object> secondAttempt = restTemplate.postForEntity(
                baseUrl() + "/auth/register",
                registerRequest,
                Object.class
        );

        // Currently 500, since we haven't built the Global Exception
        // Handler yet (Step 12) — this test documents CURRENT behavior
        // honestly, and we'll update this assertion once that step
        // changes it to 409.
        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
