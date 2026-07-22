package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.PaymentType;
import com.parcelflow.common.enums.ServiceType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateOrderRequest {
    /** Column is VARCHAR(500); rejecting here beats a constraint violation from the driver. */
    @Size(max = 500)
    private String note;

    private ServiceType serviceType;
    private PaymentType paymentType;
    private Long finalHubId;

    /**
     * Money to collect on delivery. Nothing validated this, so an order could be
     * saved owing a negative amount — PUT with {"codAmount": -999999} was
     * accepted and persisted.
     */
    @PositiveOrZero
    private BigDecimal codAmount;
}
