package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.OrderStatus;
import com.parcelflow.common.enums.PaymentType;
import com.parcelflow.common.enums.ServiceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        ServiceType serviceType,
        PaymentType paymentType,
        BigDecimal totalWeight,
        BigDecimal totalFee,
        BigDecimal codAmount,
        String note,
        Long createdHubId,
        Long currentHubId,
        Long finalHubId,
        Long createdBy,
        LocalDateTime createdAt,
        OrderPartyResponse sender,
        OrderPartyResponse receiver,
        List<ParcelResponse> parcels) {
}
