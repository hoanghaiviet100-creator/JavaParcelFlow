package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_route_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParcelRoutePlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false, unique = true)
    private Long parcelId;

    @Column(name = "planned_by", nullable = false)
    private Long plannedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutePlanStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
