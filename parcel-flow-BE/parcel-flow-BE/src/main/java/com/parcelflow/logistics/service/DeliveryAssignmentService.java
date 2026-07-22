package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.DeliveryAssignmentStatus;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import com.parcelflow.domain.DeliveryAssignment;
import com.parcelflow.domain.Parcel;
import com.parcelflow.logistics.dto.DeliveryAssignmentResponse;
import com.parcelflow.repository.DeliveryAssignmentRepository;
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
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final ParcelRepository parcelRepository;

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
        return toResponse(assignment);
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
