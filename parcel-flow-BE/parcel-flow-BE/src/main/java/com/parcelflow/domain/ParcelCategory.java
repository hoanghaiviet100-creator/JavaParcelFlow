package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParcelCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_fragile", nullable = false)
    private Boolean isFragile;

    @Column(name = "is_liquid", nullable = false)
    private Boolean isLiquid;

    @Column(name = "is_high_value", nullable = false)
    private Boolean isHighValue;

    @Column(name = "requires_special_handling", nullable = false)
    private Boolean requiresSpecialHandling;
}
