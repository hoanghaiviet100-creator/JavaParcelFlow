package com.parcelflow.domain;

import com.parcelflow.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(unique = true, length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // --- added in Flyway V2 (auth) ---
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword;

    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    // --- added in Flyway V3 (permanent lock metadata) ---
    @Column(name = "lock_reason", length = 100)
    private String lockReason;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
