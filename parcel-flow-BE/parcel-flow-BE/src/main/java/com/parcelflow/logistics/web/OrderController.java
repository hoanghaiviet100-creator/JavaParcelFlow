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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final TrackingService trackingService;

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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.update(id, request), "Order updated"));
    }

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
