package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.OrderStatus;
import com.parcelflow.common.enums.ParcelStatus;

import java.util.List;

/**
 * Derives the status of an order from the statuses of its parcels.
 *
 * <p>Before this existed, {@code orders.status} was written once at creation and
 * never again: a shipment whose only parcel had been delivered still reported
 * CREATED on the public tracking page, because nothing advanced the order when
 * {@link ParcelService#updateStatus} moved a parcel.
 *
 * <p>An order is a container for parcels, so its status is the aggregate:
 * <ol>
 *   <li>every parcel cancelled -&gt; the order is cancelled;</li>
 *   <li>cancelled parcels are otherwise ignored — they should not hold back an
 *       order whose remaining parcels are still moving;</li>
 *   <li>anything on the return path outranks normal progress, because a partial
 *       return is the fact the customer needs to see;</li>
 *   <li>a failed delivery outranks progress for the same reason;</li>
 *   <li>the order is DELIVERED only when every parcel is;</li>
 *   <li>otherwise the order sits at its <em>least advanced</em> parcel: a
 *       shipment is only as far along as its slowest box.</li>
 * </ol>
 */
public final class OrderStatusDeriver {

    private OrderStatusDeriver() {
    }

    public static OrderStatus derive(List<ParcelStatus> parcelStatuses) {
        if (parcelStatuses == null || parcelStatuses.isEmpty()) {
            return OrderStatus.CREATED;
        }

        if (parcelStatuses.stream().allMatch(s -> s == ParcelStatus.CANCELLED)) {
            return OrderStatus.CANCELLED;
        }

        List<ParcelStatus> active = parcelStatuses.stream()
                .filter(s -> s != ParcelStatus.CANCELLED)
                .toList();

        if (active.stream().allMatch(s -> s == ParcelStatus.RETURNED)) {
            return OrderStatus.RETURNED;
        }
        if (active.stream().anyMatch(s -> s == ParcelStatus.RETURNING || s == ParcelStatus.RETURNED)) {
            return OrderStatus.RETURNING;
        }
        if (active.stream().anyMatch(s -> s == ParcelStatus.DELIVERY_FAILED
                || s == ParcelStatus.LOST
                || s == ParcelStatus.DAMAGED)) {
            return OrderStatus.DELIVERY_FAILED;
        }
        if (active.stream().allMatch(s -> s == ParcelStatus.DELIVERED)) {
            return OrderStatus.DELIVERED;
        }

        return active.stream()
                .map(OrderStatusDeriver::toOrderStatus)
                .min(java.util.Comparator.comparingInt(OrderStatusDeriver::progressRank))
                .orElse(OrderStatus.CREATED);
    }

    /** Maps one parcel status onto the coarser order-level vocabulary. */
    private static OrderStatus toOrderStatus(ParcelStatus status) {
        return switch (status) {
            case CREATED -> OrderStatus.CREATED;
            case RECEIVED_AT_ORIGIN_HUB -> OrderStatus.RECEIVED_AT_ORIGIN_HUB;
            case WAITING_FOR_ROUTE, WAITING_FOR_OUTBOUND -> OrderStatus.WAITING_FOR_ROUTE;
            case IN_TRANSIT -> OrderStatus.IN_TRANSIT;
            case ARRIVED_AT_HUB, READY_FOR_DELIVERY -> OrderStatus.ARRIVED_AT_FINAL_HUB;
            case ASSIGNED_TO_SHIPPER, OUT_FOR_DELIVERY -> OrderStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> OrderStatus.DELIVERED;
            case DELIVERY_FAILED, LOST, DAMAGED -> OrderStatus.DELIVERY_FAILED;
            case RETURNING -> OrderStatus.RETURNING;
            case RETURNED -> OrderStatus.RETURNED;
            case CANCELLED -> OrderStatus.CANCELLED;
        };
    }

    /**
     * How far along the happy path a status is. Declared explicitly rather than
     * relying on {@code Enum.ordinal()}, so reordering the enum cannot silently
     * change the derivation.
     */
    private static int progressRank(OrderStatus status) {
        return switch (status) {
            case CREATED -> 0;
            case RECEIVED_AT_ORIGIN_HUB -> 1;
            case WAITING_FOR_ROUTE -> 2;
            case IN_TRANSIT -> 3;
            case ARRIVED_AT_FINAL_HUB -> 4;
            case OUT_FOR_DELIVERY -> 5;
            case DELIVERED -> 6;
            case DELIVERY_FAILED -> 7;
            case RETURNING -> 8;
            case RETURNED -> 9;
            case CANCELLED -> 10;
        };
    }
}
