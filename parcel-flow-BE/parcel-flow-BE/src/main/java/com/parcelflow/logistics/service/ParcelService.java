package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.ParcelStatus;
import com.parcelflow.common.enums.ResponsibilityType;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.ParcelCurrentState;
import com.parcelflow.domain.ParcelCustodyLog;
import com.parcelflow.logistics.dto.ParcelResponse;
import com.parcelflow.logistics.dto.UpdateParcelStatusRequest;
import com.parcelflow.repository.ParcelCurrentStateRepository;
import com.parcelflow.repository.ParcelCustodyLogRepository;
import com.parcelflow.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ParcelService {

    private final ParcelRepository parcelRepository;
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

        return toResponse(parcel);
    }

    private ParcelResponse toResponse(Parcel p) {
        return new ParcelResponse(p.getId(), p.getParcelCode(), p.getCategoryId(),
                p.getWeight(), p.getStatus());
    }
}
