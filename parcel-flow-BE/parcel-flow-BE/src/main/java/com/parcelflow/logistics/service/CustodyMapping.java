package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.CustodyActionType;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.enums.ResponsibilityType;

/**
 * Derives custody responsibility and a custody action from a parcel status transition.
 * MVP-level mapping; refine as the routing/last-mile modules land in Phase 2.
 */
public final class CustodyMapping {

    private CustodyMapping() {
    }

    public static ResponsibilityType responsibilityFor(ParcelStatus status) {
        return switch (status) {
            case IN_TRANSIT -> ResponsibilityType.DRIVER;
            case ASSIGNED_TO_SHIPPER, OUT_FOR_DELIVERY -> ResponsibilityType.SHIPPER;
            case DELIVERED -> ResponsibilityType.CUSTOMER;
            case LOST, DAMAGED, CANCELLED -> ResponsibilityType.SYSTEM;
            case CREATED, RECEIVED_AT_ORIGIN_HUB, WAITING_FOR_ROUTE, WAITING_FOR_OUTBOUND,
                 ARRIVED_AT_HUB, READY_FOR_DELIVERY, DELIVERY_FAILED, RETURNING, RETURNED
                    -> ResponsibilityType.HUB;
        };
    }

    public static CustodyActionType actionFor(ParcelStatus status) {
        return switch (status) {
            case RECEIVED_AT_ORIGIN_HUB -> CustodyActionType.RECEIVED_FROM_CUSTOMER;
            case IN_TRANSIT -> CustodyActionType.HANDOVER_TO_DRIVER;
            case ARRIVED_AT_HUB -> CustodyActionType.RECEIVED_AT_DESTINATION_HUB;
            case OUT_FOR_DELIVERY -> CustodyActionType.HANDOVER_TO_SHIPPER;
            case DELIVERED -> CustodyActionType.DELIVERED_TO_RECEIVER;
            case DELIVERY_FAILED -> CustodyActionType.DELIVERY_FAILED;
            case RETURNED -> CustodyActionType.RETURNED_TO_HUB;
            default -> CustodyActionType.EXCEPTION_REPORTED;
        };
    }
}
