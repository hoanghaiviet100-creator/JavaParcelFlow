package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.api.PageResponse;
import com.parcelflow.logistics.dto.RoutePlanResponse;
import com.parcelflow.logistics.service.RoutePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Multi-hub transit route plans. Read-only; planning/approval mutations are out of scope here.
 */
@RestController
@RequestMapping("/api/v1/route-plans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','HUB_MANAGER')")
public class RoutePlanController {

    private final RoutePlanService routePlanService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RoutePlanResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<RoutePlanResponse> page = PageResponse.from(routePlanService.list(pageable));
        return ResponseEntity.ok(ApiResponse.success(page, "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutePlanResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(routePlanService.getById(id), "OK"));
    }
}
