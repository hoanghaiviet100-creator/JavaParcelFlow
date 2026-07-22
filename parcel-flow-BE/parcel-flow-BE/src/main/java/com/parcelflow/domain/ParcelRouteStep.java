package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_route_steps")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParcelRouteStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_route_plan_id", nullable = false)
    private Long parcelRoutePlanId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "from_hub_id", nullable = false)
    private Long fromHubId;

    @Column(name = "to_hub_id", nullable = false)
    private Long toHubId;

    @Column(name = "expected_departure_at")
    private LocalDateTime expectedDepartureAt;

    @Column(name = "expected_arrival_at")
    private LocalDateTime expectedArrivalAt;

    @Column(name = "actual_departure_at")
    private LocalDateTime actualDepartureAt;

    @Column(name = "actual_arrival_at")
    private LocalDateTime actualArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStepStatus status;
}
