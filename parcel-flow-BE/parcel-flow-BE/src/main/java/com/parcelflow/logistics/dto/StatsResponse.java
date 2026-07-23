package com.parcelflow.logistics.dto;

/**
 * Live counts for the role dashboards.
 *
 * <p>Every field is computed from the current database on request. The
 * dashboards previously showed fabricated figures ("142 registered today"),
 * which is exactly the kind of thing that unravels in a demo; these are real.
 */
public record StatsResponse(
        long totalUsers,
        long activeHubs,
        long totalOrders,
        long ordersToday,
        long totalParcels,
        long parcelsInboundPending,
        long parcelsWaitingForRoute,
        long parcelsInTransit,
        long pendingDeliveries,
        long openRoutePlans,
        long openAssignments) {
}
