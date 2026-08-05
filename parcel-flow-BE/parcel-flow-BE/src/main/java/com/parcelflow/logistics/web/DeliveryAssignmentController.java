package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.api.PageResponse;
import com.parcelflow.logistics.dto.AssignableShipperResponse;
import com.parcelflow.logistics.dto.CreateDeliveryAssignmentRequest;
import com.parcelflow.logistics.dto.DeliveryAssignmentResponse;
import com.parcelflow.logistics.service.DeliveryAssignmentService;
import com.parcelflow.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dispatch-side view of all delivery assignments across shippers.
 *
 * <p>This class was read-only, and nothing anywhere else created an assignment
 * either: every row in the table came from seed data. A parcel could be marked
 * ASSIGNED_TO_SHIPPER on the scan screen, but that status names no courier, so
 * the parcel never entered anyone's queue and the delivery leg of the workflow
 * could not be started from the application at all.
 */
@RestController
@RequestMapping("/api/v1/delivery-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','HUB_MANAGER')")
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService assignmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeliveryAssignmentResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<DeliveryAssignmentResponse> page =
                PageResponse.from(assignmentService.list(pageable));
        return ResponseEntity.ok(ApiResponse.success(page, "OK"));
    }

    /**
     * Couriers this dispatcher may hand a parcel to, with their current load.
     * Served here rather than from /api/v1/users, which is ADMIN-only.
     */
    @GetMapping("/shippers")
    public ResponseEntity<ApiResponse<List<AssignableShipperResponse>>> assignableShippers() {
        return ResponseEntity.ok(
                ApiResponse.success(assignmentService.listAssignableShippers(), "OK"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponse>> create(
            @Valid @RequestBody CreateDeliveryAssignmentRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        DeliveryAssignmentResponse response =
                assignmentService.create(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Parcel assigned to courier"));
    }
}
