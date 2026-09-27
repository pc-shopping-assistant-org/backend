package com.ecm.payment.messaging;

import com.ecm.payment.entity.OutboxEvent;
import com.ecm.payment.entity.OutboxStatus;
import com.ecm.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Polls {@code outbox_events} and publishes each PENDING row to Kafka, decoupled from the
 * transaction that created the row (see docs/02-architecture/service-communication.md
 * "Outbox pattern"). A polling job here stands in for a Debezium CDC relay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final long POLL_INTERVAL_MS = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void publishPending() {
        var pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getDestination(), event.getAggregateId().toString(), event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                // 1. Leave the row PENDING so the next poll retries — a broker outage is transient.
                log.error("Failed to publish outbox event {} ({}), will retry", event.getId(), event.getEventType(), ex);
            }
        }
    }
}
