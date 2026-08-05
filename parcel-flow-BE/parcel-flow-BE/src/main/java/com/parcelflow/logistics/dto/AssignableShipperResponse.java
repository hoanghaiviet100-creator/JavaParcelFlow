package com.parcelflow.logistics.dto;

/**
 * One courier a dispatcher may hand a parcel to.
 *
 * <p>Served by the assignment endpoints rather than read from {@code /api/v1/users},
 * which is ADMIN-only — a DISPATCHER has to be able to see who is on shift without
 * being granted the whole user directory.
 *
 * @param activeAssignments parcels already in flight for this courier, so the
 *                          dispatcher can see who is loaded before adding another
 */
public record AssignableShipperResponse(
        Long userId,
        String fullName,
        Long hubId,
        Boolean isAvailable,
        Integer maxOrdersPerDay,
        long activeAssignments) {
}
