package com.parcelflow.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling (Phase 4):
 *  - retry a failed record a few times with a fixed backoff,
 *  - then publish it to "<topic>.DLT" so it is not lost and can be inspected/replayed.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public NewTopic emailNotificationsDlt() {
        return TopicBuilder.name(KafkaTopics.EMAIL_NOTIFICATIONS + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // 3 retries, 2s apart, then send to the DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
