package com.parcelflow.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Consumes email events and delivers them via {@link MailService}.
 *
 * <p>Idempotency: Kafka is at-least-once, so we de-dupe on {@code eventId} using a
 * short-lived Redis key. A duplicate delivery is dropped instead of emailing twice.
 *
 * <p>Failure handling: exceptions propagate so the container's DefaultErrorHandler can
 * retry and then route to the dead-letter topic (see {@link KafkaConsumerConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailEventConsumer {

    private static final long DEDUP_TTL_SECONDS = 24 * 60 * 60;

    private final MailService mailService;
    private final StringRedisTemplate redis;

    @KafkaListener(topics = KafkaTopics.EMAIL_NOTIFICATIONS, groupId = "parcel-flow")
    public void onEmailEvent(EmailEvent event) {
        String dedupKey = "email:sent:" + event.eventId();
        Boolean firstTime = redis.opsForValue()
                .setIfAbsent(dedupKey, "1", DEDUP_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("Skipping duplicate email event id={}", event.eventId());
            return;
        }
        try {
            mailService.send(event.to(), event.subject(), event.text());
        } catch (RuntimeException ex) {
            // Allow retry/DLT: release the dedup key so a later successful retry can send.
            redis.delete(dedupKey);
            log.error("Failed to send email event id={} to={}", event.eventId(), event.to(), ex);
            throw ex;
        }
    }
}
