package com.parcelflow.logistics.dto;

import java.util.List;

/**
 * Public order-tracking payload. Shaped to match the frontend's OrderTrackingInfo.
 *
 * <p>PII (names/addresses/phone) is only populated when the caller supplied the correct
 * receiver phone number; otherwise those fields are null and only status + timeline show.
 */
public record PublicTrackingResponse(
        String orderCode,
        String status,
        String senderName,
        String senderAddress,
        String receiverName,
        String receiverAddress,
        String phoneNumber,
        List<ParcelSummary> parcels,
        List<Event> events) {

    public record ParcelSummary(
            String parcelCode,
            double weight,
            String description) {
    }

    public record Event(
            String id,
            String status,
            String description,
            String locationName,
            String timestamp) {
    }
}
