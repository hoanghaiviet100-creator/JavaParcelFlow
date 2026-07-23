package com.parcelflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The authorisation matrix the controllers declare with @PreAuthorize, checked
 * end to end rather than trusted. A regression here is a privilege escalation,
 * so it is worth asserting each edge explicitly.
 */
class SecurityAccessIT extends AbstractIT {

    @Test
    void userAdminEndpoints_areAdminOnly() {
        assertThat(get("/api/v1/users", token(STAFF, STAFF_PW)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/users", token(DISPATCHER, DISPATCHER_PW)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/users", token(ADMIN, ADMIN_PW)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void routePlansAndAssignments_areBackOfficeOnly() {
        String shipper = token(SHIPPER, SHIPPER_PW);
        assertThat(get("/api/v1/route-plans", shipper).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/delivery-assignments", shipper).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String dispatcher = token(DISPATCHER, DISPATCHER_PW);
        assertThat(get("/api/v1/route-plans", dispatcher).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/delivery-assignments", dispatcher).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shipperQueue_isShipperOnly() {
        assertThat(get("/api/v1/shipper/assignments", token(SHIPPER, SHIPPER_PW)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/shipper/assignments", token(DISPATCHER, DISPATCHER_PW)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void protectedEndpoints_rejectMissingToken() {
        assertThat(get("/api/v1/hubs", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/orders", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/users", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userListing_neverLeaksPasswordHash() {
        ResponseEntity<String> r = get("/api/v1/users?size=100", token(ADMIN, ADMIN_PW));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = r.getBody() == null ? "" : r.getBody();
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");   // no BCrypt digest either
    }
}
