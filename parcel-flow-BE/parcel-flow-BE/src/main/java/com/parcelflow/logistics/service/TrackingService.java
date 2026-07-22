package com.parcelflow.logistics.service;

import com.parcelflow.domain.TrackingEvent;
import com.parcelflow.logistics.dto.TrackingEventResponse;
import com.parcelflow.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final TrackingEventRepository trackingEventRepository;

    public void record(Long orderId, Long parcelId, String status, String title,
                       String message, Long hubId, boolean visibleToCustomer) {
        TrackingEvent event = TrackingEvent.builder()
                .orderId(orderId)
                .parcelId(parcelId)
                .status(status)
                .title(title)
                .message(message)
                .hubId(hubId)
                .visibleToCustomer(visibleToCustomer)
                .build();
        trackingEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<TrackingEventResponse> listByOrder(Long orderId) {
        return trackingEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(e -> new TrackingEventResponse(
                        e.getId(), e.getStatus(), e.getTitle(), e.getMessage(),
                        e.getHubId(), Boolean.TRUE.equals(e.getVisibleToCustomer()), e.getCreatedAt()))
                .toList();
    }
}
