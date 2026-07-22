package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_current_state")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParcelCurrentState {
    // PK is parcel_id (shared with parcels.id), assigned manually.
    @Id
    @Column(name = "parcel_id")
    private Long parcelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30)
    private ParcelStatus currentStatus;

    @Column(name = "current_hub_id")
    private Long currentHubId;

    @Column(name = "current_user_id")
    private Long currentUserId;

    @Column(name = "current_route_step_id")
    private Long currentRouteStepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility_type", nullable = false, length = 20)
    private ResponsibilityType responsibilityType;

    @Column(name = "responsible_user_id")
    private Long responsibleUserId;

    @Column(name = "responsible_hub_id")
    private Long responsibleHubId;

    @Column(name = "last_scan_at")
    private LocalDateTime lastScanAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
