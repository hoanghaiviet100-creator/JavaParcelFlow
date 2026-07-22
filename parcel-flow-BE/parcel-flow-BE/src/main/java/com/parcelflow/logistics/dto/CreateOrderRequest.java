package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.PaymentType;
import com.parcelflow.common.enums.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull
    private Long createdHubId;
    private Long finalHubId;
    private ServiceType serviceType;
    private PaymentType paymentType;
    private BigDecimal codAmount;
    private String note;

    @NotNull
    @Valid
    private PartyRequest sender;

    @NotNull
    @Valid
    private PartyRequest receiver;

    @NotEmpty
    @Valid
    private List<ParcelRequest> parcels;
}
