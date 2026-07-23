package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.DeliveryAssignmentStatus;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.logistics.dto.StatsResponse;
import com.parcelflow.repository.DeliveryAssignmentRepository;
import com.parcelflow.repository.HubRepository;
import com.parcelflow.repository.OrderRepository;
import com.parcelflow.repository.ParcelRepository;
import com.parcelflow.repository.ParcelRoutePlanRepository;
import com.parcelflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Real counts for the dashboards, each a COUNT against the live tables. The
 * status groupings mirror the vocabulary the parcel lifecycle actually uses, so
 * a figure like "pending deliveries" means precisely the parcels a shipper still
 * has to move, not an invented number.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final List<ParcelStatus> INBOUND_PENDING =
            List.of(ParcelStatus.CREATED, ParcelStatus.RECEIVED_AT_ORIGIN_HUB);
    private static final List<ParcelStatus> WAITING_FOR_ROUTE =
            List.of(ParcelStatus.WAITING_FOR_ROUTE, ParcelStatus.WAITING_FOR_OUTBOUND);
    private static final List<ParcelStatus> IN_TRANSIT =
            List.of(ParcelStatus.IN_TRANSIT);
    private static final List<ParcelStatus> PENDING_DELIVERY =
            List.of(ParcelStatus.READY_FOR_DELIVERY, ParcelStatus.ASSIGNED_TO_SHIPPER,
                    ParcelStatus.OUT_FOR_DELIVERY);
    private static final List<DeliveryAssignmentStatus> OPEN_ASSIGNMENTS =
            List.of(DeliveryAssignmentStatus.ASSIGNED, DeliveryAssignmentStatus.ACCEPTED,
                    DeliveryAssignmentStatus.PICKED_UP, DeliveryAssignmentStatus.OUT_FOR_DELIVERY);

    private final UserRepository userRepository;
    private final HubRepository hubRepository;
    private final OrderRepository orderRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelRoutePlanRepository routePlanRepository;
    private final DeliveryAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public StatsResponse overview() {
        return new StatsResponse(
                userRepository.count(),
                hubRepository.countByIsActiveTrue(),
                orderRepository.count(),
                orderRepository.countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay()),
                parcelRepository.count(),
                parcelRepository.countByStatusIn(INBOUND_PENDING),
                parcelRepository.countByStatusIn(WAITING_FOR_ROUTE),
                parcelRepository.countByStatusIn(IN_TRANSIT),
                parcelRepository.countByStatusIn(PENDING_DELIVERY),
                routePlanRepository.count(),
                assignmentRepository.countByStatusIn(OPEN_ASSIGNMENTS));
    }
}
