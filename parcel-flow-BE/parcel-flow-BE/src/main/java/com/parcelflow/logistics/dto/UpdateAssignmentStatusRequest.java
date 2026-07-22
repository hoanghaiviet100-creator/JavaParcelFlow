package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.DeliveryAssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAssignmentStatusRequest {
    @NotNull
    private DeliveryAssignmentStatus status;
}
