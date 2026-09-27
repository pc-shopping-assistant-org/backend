package com.ecm.payment.service;

import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.entity.OutboxChannel;
import com.ecm.payment.entity.OutboxEvent;
import com.ecm.payment.entity.OutboxStatus;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentStatus;
import com.ecm.payment.mapper.PaymentMapper;
import com.ecm.payment.messaging.event.PaymentCompletedEvent;
import com.ecm.payment.messaging.event.PaymentFailedEvent;
import com.ecm.payment.messaging.kafka.KafkaTopics;
import com.ecm.payment.repository.OutboxEventRepository;
import com.ecm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String AGGREGATE_TYPE_PAYMENT = "PAYMENT";

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    @SneakyThrows
    public PaymentResponse handleWebhook(UUID paymentId, PaymentWebhookRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // 1. A terminal payment cannot be re-notified into a different outcome.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateException("Payment", paymentId, payment.getStatus().name(), "report webhook result for");
        }

        // 2. Apply the gateway's outcome to the payment record.
        boolean paid = "PAID".equalsIgnoreCase(request.status());
        payment.setStatus(paid ? PaymentStatus.PAID : PaymentStatus.FAILED);
        payment.setProviderTransactionCode(request.providerTransactionCode());
        if (paid) {
            payment.setPaidAt(Instant.now());
        }
        payment = paymentRepository.save(payment);

        // 3. Write the outcome event in the same transaction as the status change — the
        // outbox relay publishes it to Kafka afterward (see docs "Outbox pattern").
        UUID eventId = UUID.randomUUID();
        String topic = paid ? KafkaTopics.PAYMENT_COMPLETED : KafkaTopics.PAYMENT_FAILED;
        String eventType = paid ? "PaymentCompletedEvent" : "PaymentFailedEvent";
        String eventPayload = paid
                ? objectMapper.writeValueAsString(new PaymentCompletedEvent(eventId, payment.getOrderId(), payment.getId()))
                : objectMapper.writeValueAsString(new PaymentFailedEvent(eventId, payment.getOrderId(), payment.getId(), "Payment gateway reported failure"));

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AGGREGATE_TYPE_PAYMENT)
                // Keyed by orderId, not payment.getId(): OutboxRelay uses aggregateId as the Kafka
                // partition key, and order-service must see all payment.completed/payment.failed
                // events for the same order in order even when an order has multiple payment
                // attempts (different paymentIds) — see the partitioning discussion in chat.
                .aggregateId(payment.getOrderId())
                .eventType(eventType)
                .channel(OutboxChannel.KAFKA)
                .destination(topic)
                .payload(eventPayload)
                .status(OutboxStatus.PENDING)
                .build());

        return paymentMapper.toResponse(payment);
    }
}
