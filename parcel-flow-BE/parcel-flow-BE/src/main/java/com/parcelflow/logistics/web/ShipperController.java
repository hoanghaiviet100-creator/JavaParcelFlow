package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.logistics.dto.DeliveryAssignmentResponse;
import com.parcelflow.logistics.dto.UpdateAssignmentStatusRequest;
import com.parcelflow.logistics.service.DeliveryAssignmentService;
import com.parcelflow.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The logged-in shipper's own last-mile work queue. All endpoints are scoped to the
 * authenticated shipper (their user id doubles as the shipper id).
 */
@RestController
@RequestMapping("/api/v1/shipper")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHIPPER')")
public class ShipperController {

    private final DeliveryAssignmentService assignmentService;

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<List<DeliveryAssignmentResponse>>> myAssignments(
            @AuthenticationPrincipal AuthPrincipal principal) {
        List<DeliveryAssignmentResponse> assignments =
                assignmentService.listForShipper(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(assignments, "OK"));
    }

    @PatchMapping("/assignments/{id}/status")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAssignmentStatusRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        DeliveryAssignmentResponse updated =
                assignmentService.updateStatusForShipper(id, principal.userId(), request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(updated, "Assignment updated"));
    }
}
