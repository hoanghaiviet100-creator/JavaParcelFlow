package com.parcelflow.logistics.service;

import com.parcelflow.common.enums.PartyType;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.domain.Hub;
import com.parcelflow.domain.Order;
import com.parcelflow.domain.OrderParty;
import com.parcelflow.domain.Parcel;
import com.parcelflow.domain.TrackingEvent;
import com.parcelflow.logistics.dto.PublicTrackingResponse;
import com.parcelflow.repository.HubRepository;
import com.parcelflow.repository.OrderPartyRepository;
import com.parcelflow.repository.OrderRepository;
import com.parcelflow.repository.ParcelRepository;
import com.parcelflow.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public (unauthenticated) order tracking.
 *
 * <p>Privacy model:
 * <ul>
 *   <li>Anyone with a valid order code sees the order status and the customer-visible
 *       tracking timeline (this is the point of a tracking page).</li>
 *   <li>PII (sender/receiver names + addresses + phone) is ONLY revealed when the caller
 *       supplies the receiver's phone number and it matches. This prevents harvesting
 *       personal data by guessing/brute-forcing order codes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PublicTrackingService {

    private final OrderRepository orderRepository;
    private final OrderPartyRepository orderPartyRepository;
    private final ParcelRepository parcelRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final HubRepository hubRepository;

    @Transactional(readOnly = true)
    public PublicTrackingResponse track(String orderCode, String phone) {
        Order order = orderRepository.findByOrderCode(orderCode.trim().toUpperCase())
                // Generic 404 — do not distinguish "no such code" from other errors.
                .orElseThrow(() -> ApiException.notFound("No shipment found for that code."));

        OrderParty receiver = orderPartyRepository
                .findByOrderIdAndPartyType(order.getId(), PartyType.RECEIVER)
                .orElse(null);
        OrderParty sender = orderPartyRepository
                .findByOrderIdAndPartyType(order.getId(), PartyType.SENDER)
                .orElse(null);

        boolean phoneVerified = phone != null
                && !phone.isBlank()
                && receiver != null
                && normalize(phone).equals(normalize(receiver.getPhone()));

        // Parcels (safe to show: code, weight, description).
        List<PublicTrackingResponse.ParcelSummary> parcels = parcelRepository
                .findByOrderId(order.getId())
                .stream()
                .map(this::toParcelSummary)
                .toList();

        // Only customer-visible events, oldest first.
        Map<Long, String> hubNames = new HashMap<>();
        List<PublicTrackingResponse.Event> events = trackingEventRepository
                .findByOrderIdOrderByCreatedAtAsc(order.getId())
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getVisibleToCustomer()))
                .map(e -> toEvent(e, hubNames))
                .toList();

        return new PublicTrackingResponse(
                order.getOrderCode(),
                order.getStatus() != null ? order.getStatus().name() : null,
                phoneVerified && sender != null ? sender.getFullName() : null,
                phoneVerified && sender != null ? sender.getAddressLine() : null,
                phoneVerified && receiver != null ? receiver.getFullName() : null,
                phoneVerified && receiver != null ? receiver.getAddressLine() : null,
                phoneVerified && receiver != null ? maskPhone(receiver.getPhone()) : null,
                parcels,
                events);
    }

    private PublicTrackingResponse.ParcelSummary toParcelSummary(Parcel p) {
        return new PublicTrackingResponse.ParcelSummary(
                p.getParcelCode(),
                p.getWeight() != null ? p.getWeight().doubleValue() : 0d,
                p.getNote());
    }

    private PublicTrackingResponse.Event toEvent(TrackingEvent e, Map<Long, String> hubNames) {
        String location = null;
        if (e.getHubId() != null) {
            location = hubNames.computeIfAbsent(e.getHubId(), id ->
                    hubRepository.findById(id).map(Hub::getName).orElse(null));
        }
        return new PublicTrackingResponse.Event(
                String.valueOf(e.getId()),
                e.getStatus(),
                e.getMessage(),
                location,
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    }

    private String normalize(String phone) {
        return Optional.ofNullable(phone).orElse("").replaceAll("[^0-9]", "");
    }

    /** Reveal only the last 3 digits even to a verified caller. */
    private String maskPhone(String phone) {
        String digits = normalize(phone);
        if (digits.length() <= 3) {
            return "***";
        }
        return "*".repeat(digits.length() - 3) + digits.substring(digits.length() - 3);
    }
}
