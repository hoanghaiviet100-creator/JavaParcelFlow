package com.parcelflow.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes email events to Kafka. The main request path NEVER sends email directly;
 * it only produces an event here (Phase 4 requirement).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(EmailEvent event) {
        // Do NOT log event body/text: it may contain a temporary password (C-4).
        log.info("Publishing email event id={} to={}", event.eventId(), event.to());
        kafkaTemplate.send(KafkaTopics.EMAIL_NOTIFICATIONS, event.to(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Producer-side failure (broker down, serialization error).
                        // In production, persist to an outbox table for later retry / alerting.
                        log.error("Failed to publish email event id={} to={}",
                                event.eventId(), event.to(), ex);
                    } else {
                        log.debug("Email event id={} published to partition {} offset {}",
                                event.eventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
