package com.parcelflow.logistics.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

// Sizes mirror the column widths in order_parties, so over-long input is
// rejected as a validation error instead of surfacing as a driver-level
// constraint violation.
@Data
public class PartyRequest {
    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Size(max = 30)
    private String phone;

    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(max = 255)
    private String addressLine;
    private Long wardId;
    @NotNull
    private Long districtId;
    @NotNull
    private Long provinceId;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
