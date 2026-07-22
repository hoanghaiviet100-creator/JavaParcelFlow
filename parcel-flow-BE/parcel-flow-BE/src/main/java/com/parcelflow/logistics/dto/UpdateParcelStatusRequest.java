package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateParcelStatusRequest {
    @NotNull
    private ParcelStatus status;
    private Long hubId;
    private String note;
}
