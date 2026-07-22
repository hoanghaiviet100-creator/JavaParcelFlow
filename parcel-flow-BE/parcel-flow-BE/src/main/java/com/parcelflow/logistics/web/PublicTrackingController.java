package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.logistics.dto.PublicTrackingResponse;
import com.parcelflow.logistics.service.PublicTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, unauthenticated shipment tracking used by the customer-facing tracking page.
 * Path matches the frontend: GET /api/tracking/{orderCode}?phone=...
 */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class PublicTrackingController {

    private final PublicTrackingService trackingService;

    @GetMapping("/{orderCode}")
    public ResponseEntity<ApiResponse<Map<String, PublicTrackingResponse>>> track(
            @PathVariable String orderCode,
            @RequestParam(required = false) String phone) {
        PublicTrackingResponse tracking = trackingService.track(orderCode, phone);
        // FE expects { data: { tracking: {...} } }
        return ResponseEntity.ok(
                ApiResponse.success(Map.of("tracking", tracking), "OK"));
    }
}
