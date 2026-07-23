package com.parcelflow.auth.dto;

import java.time.LocalDateTime;

/**
 * Administrative view of an account.
 *
 * <p>Deliberately omits {@code passwordHash}: this is the only place user rows
 * leave the server, and a BCrypt digest is still credential material that an
 * admin screen has no use for.
 *
 * <p>{@code lockReason} and {@code lockedAt} are included because that is the
 * whole point of the columns — the V3 migration added them so support can audit
 * why an account was locked, and until now nothing could read them back.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String roleCode,
        Long hubId,
        boolean active,
        boolean mustChangePassword,
        LocalDateTime passwordExpiresAt,
        String lockReason,
        LocalDateTime lockedAt,
        LocalDateTime createdAt) {
}
