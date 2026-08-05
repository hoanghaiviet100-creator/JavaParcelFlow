package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.OrderStatus;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.enums.ResponsibilityType;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import com.parcelflow.domain.Order;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.ParcelCurrentState;
import com.parcelflow.domain.ParcelCustodyLog;
import com.parcelflow.logistics.dto.ParcelResponse;
import com.parcelflow.logistics.dto.ParcelTransitionsResponse;
import com.parcelflow.logistics.dto.UpdateParcelStatusRequest;
import com.parcelflow.repository.OrderRepository;
import com.parcelflow.repository.ParcelCurrentStateRepository;
import com.parcelflow.repository.ParcelCustodyLogRepository;
import com.parcelflow.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParcelService {

    private final ParcelRepository parcelRepository;
    private final OrderRepository orderRepository;
    private final ParcelCurrentStateRepository currentStateRepository;
    private final ParcelCustodyLogRepository custodyLogRepository;
    private final TrackingService trackingService;

    @Transactional(readOnly = true)
    public ParcelResponse getById(Long id) {
        Parcel parcel = parcelRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + id));
        return toResponse(parcel);
    }

    @Transactional(readOnly = true)
    public Page<ParcelResponse> list(Pageable pageable) {
        return parcelRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ParcelResponse getByCode(String parcelCode) {
        Parcel parcel = parcelRepository.findByParcelCode(parcelCode)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + parcelCode));
        return toResponse(parcel);
    }

    /**
     * Status write for callers that act on the parcel's behalf rather than as a
     * person — today the shipper assignment flow. These only ever request ordinary
     * forward movement, so they get no supervisor rights.
     */
    @Transactional
    public ParcelResponse updateStatus(Long parcelId, UpdateParcelStatusRequest req, Long actingUserId) {
        return updateStatus(parcelId, req, actingUserId, null);
    }

    @Transactional
    public ParcelResponse updateStatus(Long parcelId, UpdateParcelStatusRequest req,
                                       Long actingUserId, String actingRole) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + parcelId));

        ParcelStatus next = req.getStatus();
        ParcelStatus previousStatus = parcel.getStatus();
        // Re-scanning the same status is legitimate (same state, new hub), so only a
        // real change is checked against the state machine.
        boolean correction = previousStatus != next
                && !ParcelStatusTransitions.allowedFrom(previousStatus).contains(next);
        if (correction) {
            requireCorrectionRights(previousStatus, next, actingRole);
        }

        parcel.setStatus(next);
        parcelRepository.save(parcel);

        ParcelCurrentState state = currentStateRepository.findById(parcelId)
                .orElseGet(() -> {
                    ParcelCurrentState s = new ParcelCurrentState();
                    s.setParcelId(parcelId);
                    return s;
                });
        ResponsibilityType previous = state.getResponsibilityType();
        ResponsibilityType current = CustodyMapping.responsibilityFor(next);

        state.setCurrentStatus(next);
        if (req.getHubId() != null) {
            state.setCurrentHubId(req.getHubId());
        }
        state.setResponsibilityType(current);
        state.setResponsibleHubId(req.getHubId());
        state.setResponsibleUserId(actingUserId);
        state.setLastScanAt(LocalDateTime.now());
        currentStateRepository.save(state);

        ParcelCustodyLog logEntry = ParcelCustodyLog.builder()
                .parcelId(parcelId)
                .fromResponsibilityType(previous)
                .toResponsibilityType(current)
                .toHubId(req.getHubId())
                .toUserId(actingUserId)
                .actionType(CustodyMapping.actionFor(next))
                .note(req.getNote())
                .createdBy(actingUserId)
                .build();
        custodyLogRepository.save(logEntry);

        trackingService.record(parcel.getOrderId(), parcelId, next.name(),
                "Parcel " + next.name(),
                correction
                        ? "Parcel status corrected from " + previousStatus.name() + " to " + next.name()
                        : "Parcel status changed to " + next.name(),
                req.getHubId(), true);

        // Only a supervisor deliberately walking a parcel back out of CANCELLED may
        // revive its order; see syncOrderStatus.
        syncOrderStatus(parcel.getOrderId(), req.getHubId(),
                correction && previousStatus == ParcelStatus.CANCELLED);

        return toResponse(parcel);
    }

    /**
     * A transition outside {@link ParcelStatusTransitions#allowedFrom} is either an
     * impossible jump (rejected outright) or a known repair (allowed for supervisors).
     * The rejection message lists what <em>is</em> reachable, so a mis-scan tells the
     * operator what to do next instead of just refusing.
     */
    private void requireCorrectionRights(ParcelStatus current, ParcelStatus next, String actingRole) {
        if (!ParcelStatusTransitions.correctionsFrom(current).contains(next)) {
            throw ApiException.conflict("A parcel cannot go from " + current.name()
                    + " to " + next.name() + ". Allowed from " + current.name() + ": "
                    + describe(ParcelStatusTransitions.allowedFrom(current)));
        }
        if (!ParcelStatusTransitions.maySupervise(actingRole)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN,
                    "Correcting a parcel from " + current.name() + " to " + next.name()
                            + " requires ADMIN or HUB_MANAGER");
        }
    }

    private static String describe(Set<ParcelStatus> statuses) {
        return statuses.isEmpty()
                ? "nothing (this is a final status)"
                : statuses.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    /** The statuses this parcel may move to, split by whether a repair is involved. */
    @Transactional(readOnly = true)
    public ParcelTransitionsResponse transitionsFor(Long parcelId, String actingRole) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + parcelId));
        ParcelStatus current = parcel.getStatus();
        return new ParcelTransitionsResponse(
                current,
                ParcelStatusTransitions.allowedFrom(current).stream().sorted().toList(),
                ParcelStatusTransitions.maySupervise(actingRole)
                        ? ParcelStatusTransitions.correctionsFrom(current).stream().sorted().toList()
                        : List.of());
    }

    /**
     * Roll the parcel's movement up to its order.
     *
     * <p>Without this the order keeps whatever status it was created with, so a
     * fully delivered shipment still reads "CREATED" on the public tracking page.
     * A tracking event is recorded only when the derived status actually changes,
     * to keep the timeline free of no-op entries.
     *
     * <p>The order-level event is written as internal-only. The parcel event
     * recorded by the caller already tells the customer what happened, and the
     * order's own status is shown in the tracking header, so publishing both
     * doubled every step of the public timeline. Operations staff still get the
     * full audit trail through the authenticated tracking-events endpoint.
     *
     * @param reinstating a supervisor has just corrected this order's parcel out of
     *                    CANCELLED, so the order is meant to come back with it
     */
    private void syncOrderStatus(Long orderId, Long hubId, boolean reinstating) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        // A cancelled order stays cancelled; ordinary parcel movement must not revive
        // it, because OrderService.cancel() cancels the order without touching its
        // parcels and that decision has to stick.
        //
        // The exception is a supervisor correction out of CANCELLED. Without it the
        // guard turned a single mis-scan into an unrecoverable order: the parcel could
        // be repaired but the order stayed CANCELLED for good, and the only way back
        // was an UPDATE against MySQL.
        if (order.getStatus() == OrderStatus.CANCELLED && !reinstating) {
            return;
        }

        List<ParcelStatus> statuses = parcelRepository.findByOrderId(orderId).stream()
                .map(Parcel::getStatus)
                .toList();
        OrderStatus derived = OrderStatusDeriver.derive(statuses);

        if (hubId != null) {
            order.setCurrentHubId(hubId);
        }
        if (derived == order.getStatus()) {
            orderRepository.save(order);
            return;
        }

        order.setStatus(derived);
        orderRepository.save(order);

        trackingService.record(orderId, null, derived.name(),
                "Order " + derived.name(),
                "Order " + order.getOrderCode() + " is now " + derived.name(), hubId, false);
    }

    private ParcelResponse toResponse(Parcel p) {
        return new ParcelResponse(p.getId(), p.getParcelCode(), p.getCategoryId(),
                p.getWeight(), p.getStatus());
    }
}
