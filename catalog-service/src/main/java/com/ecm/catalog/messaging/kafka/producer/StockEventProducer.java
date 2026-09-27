package com.ecm.catalog.messaging.kafka.producer;

import com.ecm.catalog.messaging.event.StockReserveFailedEvent;
import com.ecm.catalog.messaging.event.StockReservedEvent;
import com.ecm.catalog.messaging.kafka.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Published directly (no outbox) right after the reserve/release transaction commits —
 * the outbox pattern is reserved for order-service and payment-service in this project
 * (see docs/02-architecture/service-communication.md), so this accepts the small window
 * where a broker outage between commit and publish could drop the event.
 */
@Component
@RequiredArgsConstructor
public class StockEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void publishStockReserved(StockReservedEvent event) {
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVED, event.orderId().toString(), objectMapper.writeValueAsString(event));
    }

    @SneakyThrows
    public void publishStockReserveFailed(StockReserveFailedEvent event) {
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVE_FAILED, event.orderId().toString(), objectMapper.writeValueAsString(event));
    }
}
