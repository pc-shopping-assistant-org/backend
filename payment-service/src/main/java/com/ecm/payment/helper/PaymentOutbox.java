package com.ecm.payment.helper;

import com.ecm.payment.entity.OutboxChannel;
import com.ecm.payment.entity.OutboxEvent;
import com.ecm.payment.entity.OutboxStatus;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.messaging.event.PaymentCompletedEvent;
import com.ecm.payment.messaging.event.PaymentFailedEvent;
import com.ecm.payment.messaging.kafka.KafkaTopics;
import com.ecm.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Writes the outcome of a payment to the outbox; the relay publishes it to Kafka afterward (see docs "Outbox pattern"). */
@Component
@RequiredArgsConstructor
public class PaymentOutbox {

    private static final String AGGREGATE_TYPE_PAYMENT = "PAYMENT";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /** Call in the transaction that changed the payment, so the event exists only if the change does. */
    public void recordOutcome(Payment payment, boolean paid, String failureReason) {
        UUID eventId = UUID.randomUUID();
        String topic = paid ? KafkaTopics.PAYMENT_COMPLETED : KafkaTopics.PAYMENT_FAILED;
        String eventType = paid ? "PaymentCompletedEvent" : "PaymentFailedEvent";
        Object event = paid
                ? new PaymentCompletedEvent(eventId, payment.getOrderId(), payment.getId())
                : new PaymentFailedEvent(eventId, payment.getOrderId(), payment.getId(), failureReason);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE_PAYMENT)
                // Keyed by orderId, not payment.getId(): OutboxRelay uses aggregateId as the Kafka
                // partition key, and order-service must see all payment.completed/payment.failed
                // events for the same order in order even when an order has multiple payment
                // attempts (different paymentIds).
                .aggregateId(payment.getOrderId())
                .eventType(eventType)
                .channel(OutboxChannel.KAFKA)
                .destination(topic)
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .build());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize " + event.getClass().getSimpleName(), ex);
        }
    }
}
