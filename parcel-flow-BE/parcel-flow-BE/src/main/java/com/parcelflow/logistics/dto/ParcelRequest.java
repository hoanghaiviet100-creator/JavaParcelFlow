package com.parcelflow.logistics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ParcelRequest {
    private Long categoryId;

    @NotNull
    @Positive
    private BigDecimal weight;

    // Dimensions are optional, but a zero or negative box is not a box.
    @Positive
    private BigDecimal length;
    @Positive
    private BigDecimal width;
    @Positive
    private BigDecimal height;

    /** Was unvalidated alongside weight: a negative declared value was accepted and stored. */
    @PositiveOrZero
    private BigDecimal declaredValue;

    @Size(max = 500)
    private String note;
}
