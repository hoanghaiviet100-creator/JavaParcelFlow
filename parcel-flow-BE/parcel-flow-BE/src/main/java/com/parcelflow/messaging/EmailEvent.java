package com.parcelflow.messaging;

import java.util.UUID;

/**
 * Email message carried over Kafka.
 *
 * <p>{@code eventId} is a stable idempotency key so the consumer can drop duplicate
 * deliveries (Kafka is at-least-once). {@code text} is the plain-text body.
 */
public record EmailEvent(String eventId, String to, String subject, String text) {

    public EmailEvent {
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.randomUUID().toString();
        }
    }

    public EmailEvent(String to, String subject, String text) {
        this(UUID.randomUUID().toString(), to, subject, text);
    }
}
