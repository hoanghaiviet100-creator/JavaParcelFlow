package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.api.PageResponse;
import com.parcelflow.logistics.dto.ParcelResponse;
import com.parcelflow.logistics.dto.ParcelTransitionsResponse;
import com.parcelflow.logistics.dto.UpdateParcelStatusRequest;
import com.parcelflow.logistics.service.ParcelService;
import com.parcelflow.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Parcel lookup and hub scanning.
 *
 * <p>Reads are open to any authenticated staff member. The status scan is not:
 * it is the write that drives custody, the customer timeline and the order
 * roll-up, so it is restricted to the roles the permission matrix grants
 * UPDATE_PARCEL_STATUS — ADMIN, HUB_MANAGER and HUB_STAFF.
 *
 * <p>A SHIPPER is deliberately excluded here even though a shipper does move
 * parcels: they do it through {@code PATCH /api/v1/shipper/assignments/{id}/status},
 * which checks that the assignment is theirs and then propagates to the parcel
 * internally. Reaching this endpoint directly would let any shipper set any
 * parcel in the system to any status, bypassing that ownership check.
 */
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ParcelResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<ParcelResponse> page = PageResponse.from(parcelService.list(pageable));
        return ResponseEntity.ok(ApiResponse.success(page, "OK"));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<ApiResponse<ParcelResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(parcelService.getByCode(code), "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ParcelResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(parcelService.getById(id), "OK"));
    }

    /**
     * The statuses this parcel may move to next, for the calling role.
     *
     * <p>Open to the same roles as the scan itself. Supervisor-only repairs come back
     * in a separate list, empty unless the caller may actually perform them.
     */
    @PreAuthorize("hasAnyRole('ADMIN','HUB_MANAGER','HUB_STAFF')")
    @GetMapping("/{id}/transitions")
    public ResponseEntity<ApiResponse<ParcelTransitionsResponse>> transitions(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ParcelTransitionsResponse response = parcelService.transitionsFor(id, principal.role());
        return ResponseEntity.ok(ApiResponse.success(response, "OK"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','HUB_MANAGER','HUB_STAFF')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ParcelResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateParcelStatusRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ParcelResponse response = parcelService.updateStatus(id, request,
                principal.userId(), principal.role());
        return ResponseEntity.ok(ApiResponse.success(response, "Parcel status updated"));
    }
}
