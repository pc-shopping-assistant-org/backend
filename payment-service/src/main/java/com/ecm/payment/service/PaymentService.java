package com.ecm.payment.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.payment.dto.request.CreatePaymentRequest;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.response.PaymentMethodResponse;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.entity.*;
import com.ecm.payment.mapper.PaymentMapper;
import com.ecm.payment.messaging.event.PaymentCompletedEvent;
import com.ecm.payment.messaging.event.PaymentFailedEvent;
import com.ecm.payment.messaging.kafka.KafkaTopics;
import com.ecm.payment.repository.OutboxEventRepository;
import com.ecm.payment.repository.PaymentMethodRepository;
import com.ecm.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String AGGREGATE_TYPE_PAYMENT = "PAYMENT";
    private static final String WEBHOOK_STATUS_PAID = "PAID";

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getActivePaymentMethods() {
        return paymentMapper.toMethodResponseList(paymentMethodRepository.findByStatusOrderByNameAsc(PaymentMethodStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrder(UUID orderId) {
        return paymentMapper.toResponseList(paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        // 1. A retried call (Feign timeout, event redelivery) with the same idempotency key
        // must not create a second row — return the one already created by the first call.
        if (request.idempotencyKey() != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return paymentMapper.toResponse(existing.get());
            }
        }

        // 2. Persist the new payment attempt. A concurrent retry with the same key can still
        // race past the check above — the unique index is the real guard. A Postgres
        // transaction is aborted after any constraint violation, so this can't recover by
        // querying again in the same transaction; translate to a clean error instead and let
        // the caller retry, which will then hit the fast path in step 1.
        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(PaymentStatus.PENDING);
        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "A payment for this attempt was already created concurrently; please retry", ex);
        }
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
        boolean paid = WEBHOOK_STATUS_PAID.equalsIgnoreCase(request.status());
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
