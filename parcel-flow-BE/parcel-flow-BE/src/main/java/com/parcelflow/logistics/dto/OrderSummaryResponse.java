package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        BigDecimal totalWeight,
        BigDecimal codAmount,
        LocalDateTime createdAt) {
}
