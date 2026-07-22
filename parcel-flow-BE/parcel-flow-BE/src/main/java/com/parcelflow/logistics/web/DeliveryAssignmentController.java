package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.api.PageResponse;
import com.parcelflow.logistics.dto.DeliveryAssignmentResponse;
import com.parcelflow.logistics.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispatch-side view of all delivery assignments across shippers.
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
}
