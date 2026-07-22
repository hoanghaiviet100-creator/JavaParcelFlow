package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_custody_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParcelCustodyLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false)
    private Long parcelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_responsibility_type", length = 20)
    private ResponsibilityType fromResponsibilityType;

    @Column(name = "from_user_id")
    private Long fromUserId;

    @Column(name = "from_hub_id")
    private Long fromHubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_responsibility_type", nullable = false, length = 20)
    private ResponsibilityType toResponsibilityType;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Column(name = "to_hub_id")
    private Long toHubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private CustodyActionType actionType;

    @Column(name = "related_route_step_id")
    private Long relatedRouteStepId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
