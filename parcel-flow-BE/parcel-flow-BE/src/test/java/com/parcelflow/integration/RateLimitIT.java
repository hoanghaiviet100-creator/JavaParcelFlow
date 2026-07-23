package com.parcelflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the RateLimitFilter turns callers away once the limit is reached.
 *
 * <p>Uses MockMvc, which drives the servlet filter chain in-process. Doing this
 * over real sockets (TestRestTemplate) was both slow and timing-dependent in a
 * containerised CI environment — the fixed window could roll over between
 * requests. In-process calls are instant, so all requests land inside one
 * window and the count is deterministic.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIT {

    private static final int TRACKING_LIMIT = 5;

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("parcel_flow");
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("app.bootstrap-admin.enabled", () -> "false");
        registry.add("app.rate-limit.tracking.max-requests", () -> String.valueOf(TRACKING_LIMIT));
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicTracking_isThrottledPerIp() throws Exception {
        // The code does not exist, so the first requests are ordinary 404s until
        // the limit trips — proving the throttle counts the request, not the result.
        for (int i = 0; i < TRACKING_LIMIT; i++) {
            mockMvc.perform(get("/api/tracking/OD20200101ZZZZZ" + i))
                    .andExpect(status().isNotFound());
        }

        // Everything past the limit is rejected before reaching the controller.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/tracking/OD20200101YYYYYY" + i))
                    .andExpect(status().isTooManyRequests());
        }
    }
}
