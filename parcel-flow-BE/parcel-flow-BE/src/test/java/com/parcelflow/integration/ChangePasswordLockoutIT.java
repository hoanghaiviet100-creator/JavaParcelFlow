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
 * Regression test for C-1: the /change-password endpoint must enforce the SAME
 * brute-force lockout as /login, otherwise it is an unauthenticated password oracle.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChangePasswordLockoutIT {

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
        if (userRepository.findByEmail("cp@example.com").isEmpty()) {
            Role role = roleRepository.findByCode("HUB_STAFF").orElseThrow();
            userRepository.save(User.builder()
                    .fullName("Change Pw")
                    .email("cp@example.com")
                    .passwordHash(passwordEncoder.encode("Correct@123"))
                    .roleId(role.getId())
                    .isActive(true)
                    .mustChangePassword(false)
                    .build());
        }
    }

    @Test
    void repeatedWrongCurrentPassword_locksAccount() {
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> r = changePassword("cp@example.com", "Wrong@123", "NewStrong@123");
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        // 3rd wrong attempt triggers the temporary lock (423), not another 401.
        ResponseEntity<String> third = changePassword("cp@example.com", "Wrong@123", "NewStrong@123");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        // While locked, even the correct current password is rejected.
        ResponseEntity<String> correct = changePassword("cp@example.com", "Correct@123", "NewStrong@123");
        assertThat(correct.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    private ResponseEntity<String> changePassword(String email, String current, String next) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "email", email,
                "currentPassword", current,
                "newPassword", next);
        return rest.postForEntity("/api/v1/auth/change-password",
                new HttpEntity<>(body, headers), String.class);
    }
}
