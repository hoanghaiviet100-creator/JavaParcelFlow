package com.parcelflow.logistics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartyRequest {
    @NotBlank
    private String fullName;
    @NotBlank
    private String phone;
    private String email;
    @NotBlank
    private String addressLine;
    private Long wardId;
    @NotNull
    private Long districtId;
    @NotNull
    private Long provinceId;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
