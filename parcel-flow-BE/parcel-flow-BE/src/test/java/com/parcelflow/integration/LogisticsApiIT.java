package com.parcelflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order → parcel → custody → tracking, plus the validation and error-mapping
 * behaviour that a run of the app has to get right. Exercises the real seeded
 * hubs and geography from Flyway V4.
 */
class LogisticsApiIT extends AbstractIT {

    private static final String ORDER = """
            {"createdHubId":1,"finalHubId":2,"serviceType":"STANDARD","paymentType":"COD",
             "codAmount":450000,"note":"office hours",
             "sender":{"fullName":"Shop ABC","phone":"0912345678","email":"shop@example.com",
                       "addressLine":"120 Le Loi","wardId":2,"districtId":1,"provinceId":1},
             "receiver":{"fullName":"Nguyen Thi Hoa","phone":"0987654321",
                         "addressLine":"55 Nguyen Thi Thap","wardId":4,"districtId":3,"provinceId":1},
             "parcels":[{"categoryId":4,"weight":1.5,"declaredValue":450000,"note":"headphones"},
                        {"categoryId":1,"weight":0.8,"declaredValue":50000,"note":"cable"}]}
            """;

    @Test
    void createOrder_sumsWeight_andReturnsCodes() {
        String staff = token(STAFF, STAFF_PW);
        ResponseEntity<String> r = post("/api/v1/orders", ORDER, staff);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = json(r).path("data");
        assertThat(data.path("orderCode").asText()).matches("OD\\d{8}[A-Z2-9]{6}");
        // 1.5 + 0.8, computed server-side from the parcels.
        assertThat(data.path("totalWeight").asDouble()).isEqualTo(2.3);
        assertThat(data.path("parcels")).hasSize(2);
    }

    @Test
    void orderValidation_rejectsBadInputWith400_notF500() {
        String staff = token(STAFF, STAFF_PW);

        assertThat(post("/api/v1/orders", "{\"createdHubId\":1,\"parcels\":[]}", staff)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/api/v1/orders", ORDER.replace("\"weight\":1.5", "\"weight\":-3"), staff)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/api/v1/orders", ORDER.replace("\"codAmount\":450000", "\"codAmount\":-1"), staff)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Unknown hub is a foreign-key failure — must surface as 400, never 500.
        assertThat(post("/api/v1/orders", ORDER.replace("\"createdHubId\":1", "\"createdHubId\":9999"), staff)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void badPathAndMethod_mapToClientErrors_notServerErrors() {
        String staff = token(STAFF, STAFF_PW);
        assertThat(get("/api/v1/parcels/not-a-number", staff).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/orders/999999", staff).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/does-not-exist", staff).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * A sort property the entity does not have is a caller typo, not a server
     * fault. Spring Data's PropertyReferenceException was unhandled, so every
     * paged endpoint answered 500 — and the default message names the mapped
     * type, leaking the internal class name.
     */
    @Test
    void unknownSortProperty_is400_notF500_andDoesNotLeakTheEntityName() {
        String staff = token(STAFF, STAFF_PW);

        for (String path : new String[]{
                "/api/v1/orders?sort=nonexistentField",
                "/api/v1/parcels?sort=bogus"}) {
            ResponseEntity<String> r = get(path, staff);
            assertThat(r.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(json(r).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(r.getBody()).doesNotContain("for type");
        }

        // A real property still sorts.
        assertThat(get("/api/v1/orders?sort=orderCode,desc", staff).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // One parcel, so the order's derived status can reach DELIVERED. An order
    // with several parcels only rolls up to DELIVERED once every parcel is
    // delivered — the derivation deliberately follows the least-advanced parcel.
    private static final String SINGLE_PARCEL_ORDER = """
            {"createdHubId":1,"finalHubId":2,"serviceType":"STANDARD","paymentType":"SENDER_PAY",
             "sender":{"fullName":"Shop ABC","phone":"0912345678",
                       "addressLine":"120 Le Loi","wardId":2,"districtId":1,"provinceId":1},
             "receiver":{"fullName":"Nguyen Thi Hoa","phone":"0987654321",
                         "addressLine":"55 Nguyen Thi Thap","wardId":4,"districtId":3,"provinceId":1},
             "parcels":[{"categoryId":1,"weight":1.0}]}
            """;

    @Test
    void parcelStatusTransitions_persistAndRollUpToOrder() {
        String staff = token(STAFF, STAFF_PW);
        JsonNode created = json(post("/api/v1/orders", SINGLE_PARCEL_ORDER, staff)).path("data");
        long parcelId = created.path("parcels").get(0).path("id").asLong();
        long orderId = created.path("id").asLong();

        for (String status : new String[]{
                "RECEIVED_AT_ORIGIN_HUB", "WAITING_FOR_ROUTE", "IN_TRANSIT",
                "ARRIVED_AT_HUB", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            ResponseEntity<String> r = patch("/api/v1/parcels/" + parcelId + "/status",
                    "{\"status\":\"" + status + "\",\"hubId\":2}", staff);
            assertThat(r.getStatusCode()).as("transition to %s", status).isEqualTo(HttpStatus.OK);
        }

        assertThat(json(get("/api/v1/parcels/" + parcelId, staff)).path("data").path("status").asText())
                .isEqualTo("DELIVERED");

        // The order followed its parcel rather than staying at CREATED.
        assertThat(json(get("/api/v1/orders/" + orderId, staff)).path("data").path("status").asText())
                .isEqualTo("DELIVERED");

        // The custody timeline was written along the way.
        JsonNode events = json(get("/api/v1/orders/" + orderId + "/tracking-events", staff)).path("data");
        assertThat(events.size()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void orderWithSeveralParcels_doesNotDeliverUntilAllParcelsDo() {
        String staff = token(STAFF, STAFF_PW);
        JsonNode created = json(post("/api/v1/orders", ORDER, staff)).path("data");   // 2 parcels
        long orderId = created.path("id").asLong();
        long firstParcel = created.path("parcels").get(0).path("id").asLong();

        for (String status : new String[]{
                "RECEIVED_AT_ORIGIN_HUB", "WAITING_FOR_ROUTE", "IN_TRANSIT",
                "ARRIVED_AT_HUB", "OUT_FOR_DELIVERY", "DELIVERED"}) {
            patch("/api/v1/parcels/" + firstParcel + "/status",
                    "{\"status\":\"" + status + "\",\"hubId\":2}", staff);
        }

        // One of two parcels delivered: the order must NOT be DELIVERED yet.
        assertThat(json(get("/api/v1/orders/" + orderId, staff)).path("data").path("status").asText())
                .isNotEqualTo("DELIVERED");
    }

    @Test
    void invalidStatusEnum_isRejectedWith400() {
        String staff = token(STAFF, STAFF_PW);
        long parcelId = json(post("/api/v1/orders", ORDER, staff))
                .path("data").path("parcels").get(0).path("id").asLong();

        assertThat(patch("/api/v1/parcels/" + parcelId + "/status",
                "{\"status\":\"NOT_A_STATUS\",\"hubId\":2}", staff).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelOrder_isLogical_andSticks() {
        String staff = token(STAFF, STAFF_PW);
        long orderId = json(post("/api/v1/orders", ORDER, staff)).path("data").path("id").asLong();

        assertThat(delete("/api/v1/orders/" + orderId, staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(get("/api/v1/orders/" + orderId, staff)).path("data").path("status").asText())
                .isEqualTo("CANCELLED");
    }

    @Test
    void publicTracking_gatesPiiBehindReceiverPhone() {
        String staff = token(STAFF, STAFF_PW);
        String orderCode = json(post("/api/v1/orders", ORDER, staff)).path("data").path("orderCode").asText();

        // No phone: status is public, PII is withheld.
        JsonNode anon = json(get("/api/tracking/" + orderCode, null)).path("data").path("tracking");
        assertThat(anon.path("status").asText()).isNotBlank();
        assertThat(anon.path("senderName").isNull()).isTrue();
        assertThat(anon.path("receiverName").isNull()).isTrue();

        // Correct receiver phone: PII revealed, but the phone itself is masked.
        JsonNode verified = json(get("/api/tracking/" + orderCode + "?phone=0987654321", null))
                .path("data").path("tracking");
        assertThat(verified.path("senderName").asText()).isEqualTo("Shop ABC");
        assertThat(verified.path("phoneNumber").asText()).endsWith("321").contains("*");

        // Wrong phone: still 200, still no PII.
        JsonNode wrong = json(get("/api/tracking/" + orderCode + "?phone=0000000000", null))
                .path("data").path("tracking");
        assertThat(wrong.path("senderName").isNull()).isTrue();
    }

    @Test
    void unknownOrderCode_tracks404() {
        assertThat(get("/api/tracking/OD20200101ZZZZZZ", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void statsOverview_returnsRealCounts_forAnyAuthenticatedUser() {
        // Requires auth.
        assertThat(get("/api/v1/stats/overview", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        JsonNode data = json(get("/api/v1/stats/overview", token(STAFF, STAFF_PW))).path("data");
        // The seed guarantees demo staff and six active hubs — real, non-zero counts.
        assertThat(data.path("totalUsers").asLong()).isGreaterThanOrEqualTo(5);
        assertThat(data.path("activeHubs").asLong()).isEqualTo(6);
        assertThat(data.has("pendingDeliveries")).isTrue();
    }
}
