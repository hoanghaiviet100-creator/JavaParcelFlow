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

    /**
     * Order and parcel writes carried no @PreAuthorize at all, so the class-level
     * default — any authenticated user — applied. A SHIPPER could create, edit and
     * cancel arbitrary orders and drive any parcel to any status. The frontend hid
     * the controls, which is not access control.
     */
    @Test
    void orderWrites_areHubRolesOnly() {
        String shipper = token(SHIPPER, SHIPPER_PW);
        String dispatcher = token(DISPATCHER, DISPATCHER_PW);

        assertThat(post("/api/v1/orders", ORDER, shipper).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/v1/orders", ORDER, dispatcher).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // The hub roles that own intake still can.
        ResponseEntity<String> created = post("/api/v1/orders", ORDER, token(STAFF, STAFF_PW));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("/api/v1/orders", ORDER, token(MANAGER, MANAGER_PW)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        long orderId = json(created).path("data").path("id").asLong();
        assertThat(put("/api/v1/orders/" + orderId, "{\"note\":\"edited\"}", shipper).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete("/api/v1/orders/" + orderId, shipper).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void parcelStatusScan_isHubRolesOnly() {
        long parcelId = json(post("/api/v1/orders", ORDER, token(STAFF, STAFF_PW)))
                .path("data").path("parcels").get(0).path("id").asLong();
        String move = "{\"status\":\"DELIVERED\",\"hubId\":2}";

        // A shipper moves parcels through their own assignment, never directly:
        // this route has no ownership check, so it must be closed to them.
        assertThat(patch("/api/v1/parcels/" + parcelId + "/status", move, token(SHIPPER, SHIPPER_PW))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch("/api/v1/parcels/" + parcelId + "/status", move, token(DISPATCHER, DISPATCHER_PW))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch("/api/v1/parcels/" + parcelId + "/status", move, token(STAFF, STAFF_PW))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Reads stay open: a dispatcher planning a route needs to look orders up. */
    @Test
    void orderAndParcelReads_stayOpenToAnyAuthenticatedStaff() {
        String shipper = token(SHIPPER, SHIPPER_PW);
        assertThat(get("/api/v1/orders", shipper).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/parcels", shipper).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static final String ORDER = """
            {"createdHubId":1,"finalHubId":2,
             "sender":{"fullName":"Shop ABC","phone":"0912345678",
                       "addressLine":"120 Le Loi","districtId":1,"provinceId":1},
             "receiver":{"fullName":"Nguyen Thi Hoa","phone":"0987654321",
                         "addressLine":"55 Nguyen Thi Thap","districtId":3,"provinceId":1},
             "parcels":[{"categoryId":1,"weight":1.0}]}
            """;

    @Test
    void userListing_neverLeaksPasswordHash() {
        ResponseEntity<String> r = get("/api/v1/users?size=100", token(ADMIN, ADMIN_PW));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = r.getBody() == null ? "" : r.getBody();
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("$2a$");   // no BCrypt digest either
    }
}
