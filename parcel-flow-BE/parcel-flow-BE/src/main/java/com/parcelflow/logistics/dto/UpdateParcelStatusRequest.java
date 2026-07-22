package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateParcelStatusRequest {
    @NotNull
    private ParcelStatus status;
    private Long hubId;

    /** Copied into parcel_custody_logs.note, which is VARCHAR(500). */
    @Size(max = 500)
    private String note;
}
