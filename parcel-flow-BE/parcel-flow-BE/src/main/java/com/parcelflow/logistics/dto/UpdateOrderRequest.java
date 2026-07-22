package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.PaymentType;
import com.parcelflow.common.enums.ServiceType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateOrderRequest {
    private String note;
    private ServiceType serviceType;
    private PaymentType paymentType;
    private Long finalHubId;
    private BigDecimal codAmount;
}
