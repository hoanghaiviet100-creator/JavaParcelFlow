package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.AssignmentType;
import com.parcelflow.common.enums.DeliveryAssignmentStatus;
import com.parcelflow.common.enums.ParcelStatus;

import java.time.LocalDateTime;

public record DeliveryAssignmentResponse(
        Long id,
        Long parcelId,
        String parcelCode,
        ParcelStatus parcelStatus,
        Long shipperId,
        DeliveryAssignmentStatus status,
        AssignmentType assignmentType,
        String assignmentReason,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime completedAt) {
}
