package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.OrderStatus;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.enums.ResponsibilityType;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.domain.Order;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.ParcelCurrentState;
import com.parcelflow.domain.ParcelCustodyLog;
import com.parcelflow.logistics.dto.ParcelResponse;
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

    @Transactional
    public ParcelResponse updateStatus(Long parcelId, UpdateParcelStatusRequest req, Long actingUserId) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + parcelId));

        ParcelStatus next = req.getStatus();
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
                "Parcel status changed to " + next.name(), req.getHubId(), true);

        syncOrderStatus(parcel.getOrderId(), req.getHubId());

        return toResponse(parcel);
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
     */
    private void syncOrderStatus(Long orderId, Long hubId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        // A cancelled order stays cancelled; parcel movement must not revive it.
        if (order.getStatus() == OrderStatus.CANCELLED) {
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
