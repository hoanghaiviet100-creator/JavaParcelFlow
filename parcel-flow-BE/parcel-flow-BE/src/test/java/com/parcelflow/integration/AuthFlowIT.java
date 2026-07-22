package com.parcelflow.integration;

import com.parcelflow.domain.Role;
import com.parcelflow.domain.User;
import com.parcelflow.repository.RoleRepository;
import com.parcelflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 representative integration test: login + temporary-lock flow.
 * Requires Docker (MySQL + Redis containers). Hibernate ddl-auto is forced to 'none'
 * here so Flyway alone owns the schema.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("parcel_flow");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("app.bootstrap-admin.enabled", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUser() {
        if (userRepository.findByEmail("staff@example.com").isEmpty()) {
            Role role = roleRepository.findByCode("HUB_STAFF").orElseThrow();
            userRepository.save(User.builder()
                    .fullName("Staff One")
                    .email("staff@example.com")
                    .passwordHash(passwordEncoder.encode("Correct@123"))
                    .roleId(role.getId())
                    .isActive(true)
                    .mustChangePassword(false)
                    .build());
        }
    }

    @Test
    void threeWrongPasswords_lockAccountWith423() {
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> r = login("staff@example.com", "Wrong@123");
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<String> third = login("staff@example.com", "Wrong@123");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        // Even the correct password is rejected while temporarily locked.
        ResponseEntity<String> correct = login("staff@example.com", "Correct@123");
        assertThat(correct.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("email", email, "password", password);
        return rest.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), String.class);
    }
}
