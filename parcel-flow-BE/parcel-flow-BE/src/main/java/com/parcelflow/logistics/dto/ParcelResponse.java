package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.ParcelStatus;

import java.math.BigDecimal;

public record ParcelResponse(
        Long id,
        String parcelCode,
        Long categoryId,
        BigDecimal weight,
        ParcelStatus status) {
}
