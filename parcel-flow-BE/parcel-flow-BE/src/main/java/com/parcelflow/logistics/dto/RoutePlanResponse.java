package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.RoutePlanStatus;

import java.time.LocalDateTime;
import java.util.List;

public record RoutePlanResponse(
        Long id,
        Long parcelId,
        Long plannedBy,
        RoutePlanStatus status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        List<RouteStepResponse> steps) {
}
