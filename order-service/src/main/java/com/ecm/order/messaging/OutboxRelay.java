package com.ecm.order.messaging;

import com.ecm.order.entity.OutboxEvent;
import com.ecm.order.entity.OutboxStatus;
import com.ecm.order.messaging.rabbitmq.RabbitTopology;
import com.ecm.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Polls {@code outbox_events} and publishes each PENDING row to its target broker, decoupled
 * from the transaction that created the row (see docs/02-architecture/service-communication.md
 * "Outbox pattern"). A polling job here stands in for a Debezium CDC relay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000)
    public void publishPending() {
        var pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            try {
                dispatch(event);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                // 1. Leave the row PENDING so the next poll retries — a broker outage is transient.
                log.error("Failed to publish outbox event {} ({}), will retry", event.getId(), event.getEventType(), ex);
            }
        }
    }

    private void dispatch(OutboxEvent event) {
        switch (event.getChannel()) {
            case KAFKA ->
                    kafkaTemplate.send(event.getDestination(), event.getAggregateId().toString(), event.getPayload());
            // "__TypeId__" lets catalog-service's ClassMapper pick the right local command
            // class by name, since the two services declare separate copies of it (see
            // service-structure.md Rule 4) rather than sharing the class itself.
            case RABBITMQ -> rabbitTemplate.send(
                    RabbitTopology.COMMANDS_EXCHANGE,
                    event.getDestination(),
                    MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                            .setContentType("application/json")
                            .setHeader("__TypeId__", event.getEventType())
                            .build());
        }
    }
}
