package com.parcelflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared base for the web-layer integration tests.
 *
 * <p>MySQL and Redis are started once as JVM-wide singletons (started in a
 * static initialiser, never explicitly stopped — Testcontainers' Ryuk reaps
 * them at exit) and reused by every subclass, so the whole suite pays the
 * container start-up cost once rather than per class.
 *
 * <p>Flyway runs all four migrations against the fresh database, so these tests
 * exercise the real seeded dataset — six hubs, the geography, the demo staff and
 * the bootstrap admin — exactly as a running instance would. ddl-auto is off so
 * Flyway alone owns the schema.
 *
 * <p>The rate-limit ceilings are lifted to an effectively unbounded value here:
 * the functional tests fire many logins in quick succession from one loopback
 * address, and the throttle is not what they are checking. {@link RateLimitIT}
 * runs on its own containers with a low limit to prove the throttle itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIT {

    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0").withDatabaseName("parcel_flow");
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // Full Flyway seed plus the bootstrap admin -> admin@parcelflow.local / Admin@12345.
        registry.add("app.bootstrap-admin.enabled", () -> "true");
        // Keep the throttle out of the way of functional assertions.
        registry.add("app.rate-limit.login.max-requests", () -> "100000");
        registry.add("app.rate-limit.tracking.max-requests", () -> "100000");
    }

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected ObjectMapper mapper;

    // Credentials seeded by V4 / AdminBootstrap.
    protected static final String ADMIN = "admin@parcelflow.local";
    protected static final String ADMIN_PW = "Admin@12345";
    protected static final String STAFF = "staff.hcm@parcelflow.local";
    protected static final String STAFF_PW = "Staff@12345";
    protected static final String DISPATCHER = "dispatcher@parcelflow.local";
    protected static final String DISPATCHER_PW = "Dispatch@12345";
    protected static final String SHIPPER = "shipper1@parcelflow.local";
    protected static final String SHIPPER_PW = "Shipper@12345";

    protected String token(String email, String password) {
        ResponseEntity<String> r = post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
        return json(r).path("data").path("accessToken").asText();
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return mapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Response was not JSON: " + response.getBody(), e);
        }
    }

    protected HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    protected ResponseEntity<String> exchange(HttpMethod method, String path, String body, String token) {
        return rest.exchange(path, method,
                new HttpEntity<>(body, headers(token)), String.class);
    }

    protected ResponseEntity<String> get(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
    }

    protected ResponseEntity<String> post(String path, String body, String token) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    protected ResponseEntity<String> put(String path, String body, String token) {
        return exchange(HttpMethod.PUT, path, body, token);
    }

    protected ResponseEntity<String> patch(String path, String body, String token) {
        return exchange(HttpMethod.PATCH, path, body, token);
    }

    protected ResponseEntity<String> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, null, token);
    }
}
