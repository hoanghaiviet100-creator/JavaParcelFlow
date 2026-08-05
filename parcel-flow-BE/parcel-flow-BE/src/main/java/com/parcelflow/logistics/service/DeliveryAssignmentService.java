package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.AssignmentType;
import com.parcelflow.common.enums.DeliveryAssignmentStatus;
import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import com.parcelflow.domain.DeliveryAssignment;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.ShipperProfile;
import com.parcelflow.domain.User;
import com.parcelflow.logistics.dto.AssignableShipperResponse;
import com.parcelflow.logistics.dto.CreateDeliveryAssignmentRequest;
import com.parcelflow.logistics.dto.DeliveryAssignmentResponse;
import com.parcelflow.logistics.dto.UpdateParcelStatusRequest;
import com.parcelflow.repository.DeliveryAssignmentRepository;
import com.parcelflow.repository.ParcelRepository;
import com.parcelflow.repository.ShipperProfileRepository;
import com.parcelflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final ParcelRepository parcelRepository;
    private final ShipperProfileRepository shipperProfileRepository;
    private final UserRepository userRepository;
    private final ParcelService parcelService;

    /** The delivery queue for one shipper (their own tasks). */
    @Transactional(readOnly = true)
    public List<DeliveryAssignmentResponse> listForShipper(Long shipperId) {
        return assignmentRepository.findByShipperIdOrderByAssignedAtDesc(shipperId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** All assignments, paged (dispatcher / admin view). */
    @Transactional(readOnly = true)
    public Page<DeliveryAssignmentResponse> list(Pageable pageable) {
        return assignmentRepository.findAll(pageable).map(this::toResponse);
    }

    /** Statuses that mean a courier still holds the parcel. */
    private static final List<DeliveryAssignmentStatus> LIVE = List.of(
            DeliveryAssignmentStatus.ASSIGNED,
            DeliveryAssignmentStatus.ACCEPTED,
            DeliveryAssignmentStatus.PICKED_UP,
            DeliveryAssignmentStatus.OUT_FOR_DELIVERY);

    /** Couriers a dispatcher may hand a parcel to, with their current load. */
    @Transactional(readOnly = true)
    public List<AssignableShipperResponse> listAssignableShippers() {
        return shipperProfileRepository.findAll().stream()
                .map(profile -> new AssignableShipperResponse(
                        profile.getUserId(),
                        userRepository.findById(profile.getUserId())
                                .map(User::getFullName)
                                .orElse("Unknown"),
                        profile.getHubId(),
                        profile.getIsAvailable(),
                        profile.getMaxOrdersPerDay(),
                        assignmentRepository.countByShipperIdAndStatusIn(profile.getUserId(), LIVE)))
                .sorted(Comparator.comparing(AssignableShipperResponse::fullName))
                .toList();
    }

    /**
     * Hand a parcel to a courier.
     *
     * <p>This is what was missing. The scan screen could set a parcel to
     * ASSIGNED_TO_SHIPPER, but that status names no courier and creates no row here,
     * so the parcel never appeared in anyone's queue -- the delivery leg of the
     * workflow could not be started from the application at all.
     *
     * <p>Creating the assignment and moving the parcel are done together, through
     * {@link ParcelService#updateStatus}, so the two can no longer disagree: one
     * write path keeps the custody log, the timeline and the order roll-up in step,
     * and the parcel's state machine decides whether the hand-off is legal at all.
     */
    @Transactional
    public DeliveryAssignmentResponse create(CreateDeliveryAssignmentRequest req, Long assignedBy) {
        Parcel parcel = parcelRepository.findById(req.getParcelId())
                .orElseThrow(() -> ApiException.notFound("Parcel not found: " + req.getParcelId()));
        ShipperProfile shipper = shipperProfileRepository.findById(req.getShipperId())
                .orElseThrow(() -> ApiException.notFound(
                        "No shipper profile for user " + req.getShipperId()));

        if (!assignmentRepository.findByParcelIdAndStatusIn(parcel.getId(), LIVE).isEmpty()) {
            throw ApiException.conflict("Parcel " + parcel.getParcelCode()
                    + " is already assigned to a courier. Cancel that assignment first.");
        }
        if (Boolean.FALSE.equals(shipper.getIsAvailable())) {
            throw ApiException.conflict("That courier is marked unavailable.");
        }

        // Let the parcel's own rules decide: a parcel still in transit, already
        // delivered or cancelled has no business being handed to a courier.
        ParcelStatus current = parcel.getStatus();
        if (current != ParcelStatus.ASSIGNED_TO_SHIPPER
                && !ParcelStatusTransitions.allowedFrom(current).contains(ParcelStatus.ASSIGNED_TO_SHIPPER)) {
            throw ApiException.conflict("A parcel in " + current.name()
                    + " cannot be assigned to a courier; it must reach READY_FOR_DELIVERY first.");
        }

        DeliveryAssignment assignment = assignmentRepository.save(DeliveryAssignment.builder()
                .parcelId(parcel.getId())
                .shipperId(shipper.getUserId())
                .assignedBy(assignedBy)
                .assignmentType(AssignmentType.MANUAL)
                .assignmentReason(req.getAssignmentReason())
                .status(DeliveryAssignmentStatus.ASSIGNED)
                .build());

        UpdateParcelStatusRequest statusReq = new UpdateParcelStatusRequest();
        statusReq.setStatus(ParcelStatus.ASSIGNED_TO_SHIPPER);
        statusReq.setHubId(shipper.getHubId());
        statusReq.setNote("Assigned to courier #" + shipper.getUserId()
                + " (assignment #" + assignment.getId() + ")");
        parcelService.updateStatus(parcel.getId(), statusReq, assignedBy);

        return toResponse(assignment);
    }

    /**
     * Shipper-driven status transition on one of their own assignments.
     * A shipper may only touch assignments that belong to them.
     */
    @Transactional
    public DeliveryAssignmentResponse updateStatusForShipper(Long assignmentId, Long shipperId,
                                                             DeliveryAssignmentStatus next) {
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment not found: " + assignmentId));
        if (!assignment.getShipperId().equals(shipperId)) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN,
                    "This delivery assignment does not belong to you");
        }

        assignment.setStatus(next);
        LocalDateTime now = LocalDateTime.now();
        switch (next) {
            case ACCEPTED -> assignment.setAcceptedAt(now);
            case PICKED_UP -> assignment.setPickedUpAt(now);
            case DELIVERED, FAILED, RETURNED_TO_HUB, CANCELLED -> assignment.setCompletedAt(now);
            default -> { /* no timestamp side-effect */ }
        }
        assignmentRepository.save(assignment);

        propagateToParcel(assignment, next, shipperId);

        return toResponse(assignment);
    }

    /**
     * Carry the parcel along with its assignment.
     *
     * <p>Without this, a shipper marking the assignment DELIVERED left the
     * parcel stuck at OUT_FOR_DELIVERY forever: no custody entry, no tracking
     * event, and the customer page never showed the delivery. Routing through
     * {@link ParcelService#updateStatus} keeps a single write path for status
     * changes — custody log, current state, timeline and the order roll-up all
     * happen exactly as they would for a hub scan.
     *
     * <p>ACCEPTED and PICKED_UP have no parcel-side equivalent (the parcel is
     * already ASSIGNED_TO_SHIPPER), and CANCELLED intentionally leaves the
     * parcel as-is for the dispatcher to reassign.
     */
    private void propagateToParcel(DeliveryAssignment assignment,
                                   DeliveryAssignmentStatus next, Long shipperId) {
        ParcelStatus parcelStatus = switch (next) {
            case OUT_FOR_DELIVERY -> ParcelStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> ParcelStatus.DELIVERED;
            case FAILED -> ParcelStatus.DELIVERY_FAILED;
            case RETURNED_TO_HUB -> ParcelStatus.RETURNING;
            default -> null;
        };
        if (parcelStatus == null) {
            return;
        }

        UpdateParcelStatusRequest req = new UpdateParcelStatusRequest();
        req.setStatus(parcelStatus);
        req.setHubId(shipperProfileRepository.findById(shipperId)
                .map(ShipperProfile::getHubId)
                .orElse(null));
        req.setNote("Delivery assignment #" + assignment.getId() + " " + next.name());
        parcelService.updateStatus(assignment.getParcelId(), req, shipperId);
    }

    private DeliveryAssignmentResponse toResponse(DeliveryAssignment a) {
        Parcel parcel = parcelRepository.findById(a.getParcelId()).orElse(null);
        return new DeliveryAssignmentResponse(
                a.getId(),
                a.getParcelId(),
                parcel != null ? parcel.getParcelCode() : null,
                parcel != null ? parcel.getStatus() : null,
                a.getShipperId(),
                a.getStatus(),
                a.getAssignmentType(),
                a.getAssignmentReason(),
                a.getAssignedAt(),
                a.getAcceptedAt(),
                a.getPickedUpAt(),
                a.getCompletedAt());
    }
}
