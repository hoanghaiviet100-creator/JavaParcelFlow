package com.parcelflow.logistics.web;

import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.api.PageResponse;
import com.parcelflow.logistics.dto.*;
import com.parcelflow.logistics.service.OrderService;
import com.parcelflow.logistics.service.TrackingService;
import com.parcelflow.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Order intake and administration.
 *
 * <p>Reads are open to any authenticated staff member — a dispatcher planning a
 * route and a shipper checking a manifest both legitimately need to look one up.
 * Writes are not: order intake belongs to the hub, so it is restricted to
 * ADMIN, HUB_MANAGER and HUB_STAFF, matching the role/permission matrix the
 * frontend already declares in {@code src/config/permissions.ts}
 * (CREATE_ORDERS / MANAGE_ORDERS).
 *
 * <p>These annotations used to be absent altogether, and the class-level default
 * is "any authenticated user". That let a SHIPPER — a role with no order
 * permission whatsoever — create, edit and cancel arbitrary orders over HTTP.
 * The frontend hid the buttons, which is not access control; the API accepted
 * the calls.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    /** Roles permitted to write orders. Reads stay open to any authenticated user. */
    private static final String HUB_WRITE_ROLES = "hasAnyRole('ADMIN','HUB_MANAGER','HUB_STAFF')";

    private final OrderService orderService;
    private final TrackingService trackingService;

    @PreAuthorize(HUB_WRITE_ROLES)
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request,
                                                            @AuthenticationPrincipal AuthPrincipal principal) {
        OrderResponse response = orderService.createOrder(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Order created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(id), "OK"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<OrderSummaryResponse> page = PageResponse.from(orderService.list(pageable));
        return ResponseEntity.ok(ApiResponse.success(page, "OK"));
    }

    @PreAuthorize(HUB_WRITE_ROLES)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.update(id, request), "Order updated"));
    }

    @PreAuthorize(HUB_WRITE_ROLES)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Order cancelled"));
    }

    @GetMapping("/{id}/tracking-events")
    public ResponseEntity<ApiResponse<List<TrackingEventResponse>>> tracking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(trackingService.listByOrder(id), "OK"));
    }
}
