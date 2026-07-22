package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipperProfile {
    // PK is user_id (shared with users.id), assigned manually.
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "max_orders_per_day", nullable = false)
    private Integer maxOrdersPerDay;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable;

    @Column(name = "current_lat", precision = 10, scale = 7)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 10, scale = 7)
    private BigDecimal currentLng;

    @Column(name = "last_location_at")
    private LocalDateTime lastLocationAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
