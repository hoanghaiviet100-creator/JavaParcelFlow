package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.ParcelStatus;

import java.util.EnumSet;
import java.util.Set;

import static com.parcelflow.common.enums.ParcelStatus.*;

/**
 * Which parcel statuses may legally follow which.
 *
 * <p>Before this existed {@link ParcelService#updateStatus} wrote whatever status
 * the request carried straight onto the parcel. CREATED -&gt; DELIVERED,
 * LOST -&gt; DELIVERED and DELIVERED -&gt; CANCELLED were all accepted, and the
 * custody log and customer timeline faithfully recorded the impossible jumps.
 *
 * <p>The transitions are split in two:
 *
 * <ul>
 *   <li>{@link #allowedFrom} — ordinary scanning, open to anyone the permission
 *       matrix grants UPDATE_PARCEL_STATUS;</li>
 *   <li>{@link #correctionsFrom} — supervisor repairs that walk a parcel back out
 *       of a terminal state, restricted to {@link #maySupervise elevated roles}.</li>
 * </ul>
 *
 * <p>The second list is the reason this class exists at all. A state machine that
 * only tightened things would have made the original problem worse: one mis-scan to
 * CANCELLED already stranded an order for good, recoverable only by editing MySQL by
 * hand. Every terminal state therefore keeps a documented way back, so operations can
 * repair a bad scan from the UI and no correction ever needs database access.
 */
public final class ParcelStatusTransitions {

    private ParcelStatusTransitions() {
    }

    /**
     * Normal forward movement. A parcel may always be re-scanned into its current
     * status (same status at a new hub is a meaningful custody event), so callers
     * check this only when the status actually changes.
     *
     * <p>LOST and DAMAGED hang off every state in which someone is holding the
     * parcel — that is exactly when either is discovered. CANCELLED is reachable
     * only before the parcel is on a vehicle; once it is moving, cancelling is a
     * return, not a cancellation.
     */
    public static Set<ParcelStatus> allowedFrom(ParcelStatus current) {
        return switch (current) {
            case CREATED -> EnumSet.of(RECEIVED_AT_ORIGIN_HUB, CANCELLED, LOST, DAMAGED);
            case RECEIVED_AT_ORIGIN_HUB -> EnumSet.of(WAITING_FOR_ROUTE, WAITING_FOR_OUTBOUND,
                    CANCELLED, LOST, DAMAGED);
            case WAITING_FOR_ROUTE -> EnumSet.of(WAITING_FOR_OUTBOUND, IN_TRANSIT,
                    CANCELLED, LOST, DAMAGED);
            case WAITING_FOR_OUTBOUND -> EnumSet.of(IN_TRANSIT, WAITING_FOR_ROUTE,
                    CANCELLED, LOST, DAMAGED);
            case IN_TRANSIT -> EnumSet.of(ARRIVED_AT_HUB, LOST, DAMAGED);
            // A parcel crossing several hubs comes back round to WAITING_FOR_ROUTE.
            case ARRIVED_AT_HUB -> EnumSet.of(WAITING_FOR_ROUTE, WAITING_FOR_OUTBOUND,
                    READY_FOR_DELIVERY, RETURNING, LOST, DAMAGED);
            case READY_FOR_DELIVERY -> EnumSet.of(ASSIGNED_TO_SHIPPER, WAITING_FOR_ROUTE,
                    RETURNING, LOST, DAMAGED);
            // DELIVERED/DELIVERY_FAILED/RETURNING are reachable directly because a
            // shipper may close an assignment without a separate OUT_FOR_DELIVERY scan.
            case ASSIGNED_TO_SHIPPER -> EnumSet.of(OUT_FOR_DELIVERY, READY_FOR_DELIVERY,
                    DELIVERED, DELIVERY_FAILED, RETURNING, LOST, DAMAGED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, DELIVERY_FAILED, RETURNING,
                    LOST, DAMAGED);
            case DELIVERY_FAILED -> EnumSet.of(ASSIGNED_TO_SHIPPER, READY_FOR_DELIVERY,
                    RETURNING, LOST, DAMAGED);
            case RETURNING -> EnumSet.of(RETURNED, ARRIVED_AT_HUB, LOST, DAMAGED);
            case DELIVERED, RETURNED, CANCELLED, LOST, DAMAGED -> EnumSet.noneOf(ParcelStatus.class);
        };
    }

    /**
     * Supervisor-only repairs out of a terminal state.
     *
     * <p>Each one undoes a specific kind of mis-scan: a parcel cancelled by mistake
     * goes back to the start of the chain, a parcel closed as DELIVERED too early
     * returns to the courier, and a parcel written off as LOST or DAMAGED rejoins the
     * network when it turns up.
     */
    public static Set<ParcelStatus> correctionsFrom(ParcelStatus current) {
        return switch (current) {
            case CANCELLED -> EnumSet.of(CREATED, RECEIVED_AT_ORIGIN_HUB);
            case DELIVERED -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERY_FAILED);
            case RETURNED -> EnumSet.of(RETURNING);
            case LOST, DAMAGED -> EnumSet.of(ARRIVED_AT_HUB, RETURNING);
            default -> EnumSet.noneOf(ParcelStatus.class);
        };
    }

    /** Repairs are a supervisor action; ordinary hub staff scan, they do not rewrite history. */
    public static boolean maySupervise(String role) {
        return "ADMIN".equals(role) || "HUB_MANAGER".equals(role);
    }
}
