package com.parcelflow.logistics.dto;

import java.time.LocalDateTime;

public record TrackingEventResponse(
        Long id,
        String status,
        String title,
        String message,
        Long hubId,
        boolean visibleToCustomer,
        LocalDateTime createdAt) {
}
