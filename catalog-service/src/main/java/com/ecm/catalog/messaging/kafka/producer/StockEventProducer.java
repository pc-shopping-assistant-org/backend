package com.ecm.catalog.messaging.kafka.producer;

import com.ecm.catalog.entity.OutboxChannel;
import com.ecm.catalog.entity.OutboxEvent;
import com.ecm.catalog.entity.OutboxStatus;
import com.ecm.catalog.messaging.event.StockReleasedEvent;
import com.ecm.catalog.messaging.event.StockReserveFailedEvent;
import com.ecm.catalog.messaging.event.StockReservedEvent;
import com.ecm.catalog.messaging.kafka.KafkaTopics;
import com.ecm.catalog.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Queues the replies of the stock saga in the outbox, so they commit together with the change of stock they report
 * and {@link com.ecm.catalog.messaging.OutboxRelay} publishes them after. The rows are also the record of what became
 * of the stock of an order: {@code StockReservedEvent} says it was taken, {@code StockReleasedEvent} that it was given back.
 */
@Component
@RequiredArgsConstructor
public class StockEventProducer {

    public static final String STOCK_RESERVED_EVENT = "StockReservedEvent";
    public static final String STOCK_RELEASED_EVENT = "StockReleasedEvent";
    private static final String STOCK_RESERVE_FAILED_EVENT = "StockReserveFailedEvent";
    private static final String AGGREGATE_TYPE_STOCK = "STOCK";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publishStockReserved(StockReservedEvent event) {
        enqueue(event.orderId(), STOCK_RESERVED_EVENT, KafkaTopics.STOCK_RESERVED, event);
    }

    public void publishStockReserveFailed(StockReserveFailedEvent event) {
        enqueue(event.orderId(), STOCK_RESERVE_FAILED_EVENT, KafkaTopics.STOCK_RESERVE_FAILED, event);
    }

    public void publishStockReleased(StockReleasedEvent event) {
        enqueue(event.orderId(), STOCK_RELEASED_EVENT, KafkaTopics.STOCK_RELEASED, event);
    }

    @SneakyThrows
    private void enqueue(UUID orderId, String eventType, String topic, Object event) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE_STOCK)
                .aggregateId(orderId)
                .eventType(eventType)
                .channel(OutboxChannel.KAFKA)
                .destination(topic)
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .build());
    }
}
