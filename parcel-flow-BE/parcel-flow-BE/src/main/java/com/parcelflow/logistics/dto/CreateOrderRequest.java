package com.parcelflow.logistics.dto;

import com.parcelflow.common.enums.PaymentType;
import com.parcelflow.common.enums.ServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

    /** Was unvalidated: a negative COD amount was accepted and stored. */
    @PositiveOrZero
    private BigDecimal codAmount;

    @Size(max = 500)
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
