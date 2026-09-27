package com.ecm.catalog.messaging;

import com.ecm.catalog.entity.InboxEvent;
import com.ecm.catalog.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Kafka/RabbitMQ deliver at-least-once; this makes re-delivery of the same command a no-op. */
@Component
@RequiredArgsConstructor
public class InboxGuard {

    private final InboxEventRepository inboxEventRepository;

    @Transactional
    public boolean alreadyProcessed(UUID eventId, String consumerName) {
        if (inboxEventRepository.existsById(eventId)) {
            return true;
        }
        inboxEventRepository.save(InboxEvent.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .build());
        return false;
    }
}
