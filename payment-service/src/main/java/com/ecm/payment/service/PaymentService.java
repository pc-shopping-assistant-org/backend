package com.ecm.payment.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.payment.client.IdentityServiceClient;
import com.ecm.payment.dto.request.AdminPaymentSearchRequest;
import com.ecm.payment.dto.request.PaymentWebhookRequest;
import com.ecm.payment.dto.response.AdminPaymentMethodResponse;
import com.ecm.payment.dto.response.AdminPaymentResponse;
import com.ecm.payment.dto.response.PaymentMethodResponse;
import com.ecm.payment.dto.response.PaymentResponse;
import com.ecm.payment.entity.OutboxChannel;
import com.ecm.payment.entity.OutboxEvent;
import com.ecm.payment.entity.OutboxStatus;
import com.ecm.payment.entity.Payment;
import com.ecm.payment.entity.PaymentMethod;
import com.ecm.payment.entity.PaymentMethodStatus;
import com.ecm.payment.entity.PaymentStatus;
import com.ecm.payment.exception.PaymentErrorCode;
import com.ecm.payment.mapper.PaymentMapper;
import com.ecm.payment.dto.request.CreatePaymentRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String AGGREGATE_TYPE_PAYMENT = "PAYMENT";
    private static final String WEBHOOK_STATUS_PAID = "PAID";
    private static final String CASH_ON_DELIVERY_CODE = "COD";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant NO_UPPER_BOUND = Instant.parse("9999-12-31T00:00:00Z");
    private static final UUID NO_ID = new UUID(0L, 0L);

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdentityServiceClient identityServiceClient;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getActivePaymentMethods() {
        return paymentMapper.toMethodResponseList(paymentMethodRepository.findByStatusOrderByNameAsc(PaymentMethodStatus.ACTIVE));
    }

    /** Every method the shop has set up, active or not (UC-ADM-PAY-001). */
    @Transactional(readOnly = true)
    public List<AdminPaymentMethodResponse> getAllPaymentMethods() {
        return paymentMapper.toAdminMethodResponseList(paymentMethodRepository.findAllByOrderByNameAsc());
    }

    /** The payment attempts of an order, oldest first; a customer sees only their own, so {@code customerId} is null for an employee. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrder(UUID orderId, UUID customerId) {
        List<Payment> payments = customerId == null
                ? paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId)
                : paymentRepository.findByOrderIdAndCustomerIdOrderByCreatedAtAsc(orderId, customerId);
        return paymentMapper.toResponseList(payments);
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
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // 1. Only an online payment is settled by the gateway, and a terminal payment cannot be re-notified into a different outcome.
        if (CASH_ON_DELIVERY_CODE.equals(methodOf(payment).getCode())) {
            throw new BusinessException(PaymentErrorCode.WEBHOOK_NOT_ALLOWED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidStateException("Payment", paymentId, payment.getStatus().name(), "report webhook result for");
        }
        if (request.providerTransactionCode() != null && paymentRepository.existsByProviderTransactionCodeAndIdNot(request.providerTransactionCode(), paymentId)) {
            throw new BusinessException(PaymentErrorCode.DUPLICATE_TRANSACTION_CODE);
        }

        // 2. Apply the gateway's outcome
        return paymentMapper.toResponse(settleOnline(payment, WEBHOOK_STATUS_PAID.equalsIgnoreCase(request.status()),
                request.providerTransactionCode(), "Payment gateway reported failure"));
    }

    /** Records what the payment gateway reported and tells the order through the outbox; the payment is locked and pending. */
    @Transactional
    @SneakyThrows
    public Payment settleOnline(Payment payment, boolean paid, String providerTransactionCode, String failureReason) {
        // 1. Apply the gateway's outcome to the payment record.
        payment.setStatus(paid ? PaymentStatus.PAID : PaymentStatus.FAILED);
        payment.setProviderTransactionCode(providerTransactionCode);
        if (paid) {
            payment.setPaidAt(Instant.now());
        }
        payment = paymentRepository.save(payment);

        // 2. Write the outcome event in the same transaction as the status change — the
        // outbox relay publishes it to Kafka afterward (see docs "Outbox pattern").
        UUID eventId = UUID.randomUUID();
        String topic = paid ? KafkaTopics.PAYMENT_COMPLETED : KafkaTopics.PAYMENT_FAILED;
        String eventType = paid ? "PaymentCompletedEvent" : "PaymentFailedEvent";
        String eventPayload = paid
                ? objectMapper.writeValueAsString(new PaymentCompletedEvent(eventId, payment.getOrderId(), payment.getId()))
                : objectMapper.writeValueAsString(new PaymentFailedEvent(eventId, payment.getOrderId(), payment.getId(), failureReason));

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

        return payment;
    }

    /** The order was cancelled: a payment still waiting for the customer has nothing left to settle. A paid one stays, for a refund by hand. */
    @Transactional
    public void cancelPendingPayments(UUID orderId) {
        paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)
                .forEach(payment -> payment.setStatus(PaymentStatus.CANCELLED));
    }

    /** The transactions of the shop, newest first (UC-ADM-PAY-003). */
    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> searchPayments(AdminPaymentSearchRequest filter, int page, int size, String bearerToken) {
        // 1. Reject a page or a period that makes no sense
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || filter.createdFrom() != null && filter.createdTo() != null && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        // 2. Which customers: the one asked for, and those whose name matches, which only the Identity Service knows
        List<UUID> customerIds = customersToSearch(filter, bearerToken);
        if (customerIds != null && customerIds.isEmpty()) {
            return PageResponse.of(new PageImpl<>(List.of(), pageable, 0));
        }

        // 3. The page of payments
        Page<Payment> payments = paymentRepository.search(filter.status() == null, filter.status(),
                filter.orderId() == null, filter.orderId() == null ? NO_ID : filter.orderId(),
                customerIds == null, customerIds == null ? List.of(NO_ID) : customerIds,
                filter.transactionCode() == null ? "" : filter.transactionCode().trim(),
                filter.createdFrom() == null ? Instant.EPOCH : filter.createdFrom(),
                filter.createdTo() == null ? NO_UPPER_BOUND : filter.createdTo(), pageable);
        return PageResponse.of(payments.map(paymentMapper::toAdminResponse));
    }

    /** Null when the search is not narrowed by customer. */
    private List<UUID> customersToSearch(AdminPaymentSearchRequest filter, String bearerToken) {
        boolean byName = filter.customerName() != null && !filter.customerName().isBlank();
        if (!byName) {
            return filter.customerId() == null ? null : List.of(filter.customerId());
        }
        List<UUID> named = Objects.requireNonNull(identityServiceClient.findCustomerIds(filter.customerName().trim(), bearerToken).getData());
        return filter.customerId() == null ? named : named.stream().filter(filter.customerId()::equals).toList();
    }

    /**
     * What an employee may set by hand (UC-ADM-PAY-002): mark a cash-on-delivery payment as paid when the cash is
     * collected, or mark a paid payment as refunded once the money went back to the customer. An online payment is
     * paid or failed only by the gateway, so the two never disagree.
     */
    @Transactional
    public AdminPaymentResponse updateStatus(UUID paymentId, PaymentStatus status, UUID employeeId) {
        // 1. Lock the payment so a webhook or an order cancellation cannot change it meanwhile
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // 2. Only these two changes are the shop's to make
        boolean cashCollected = payment.getStatus() == PaymentStatus.PENDING && status == PaymentStatus.PAID
                && CASH_ON_DELIVERY_CODE.equals(methodOf(payment).getCode());
        boolean refunded = payment.getStatus() == PaymentStatus.PAID && status == PaymentStatus.REFUNDED;
        if (!cashCollected && !refunded) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_NOT_EDITABLE);
        }

        // 3. Apply it, recording who did
        payment.setStatus(status);
        payment.setUpdatedBy(employeeId);
        if (cashCollected) {
            payment.setPaidAt(Instant.now());
        }
        try {
            return paymentMapper.toAdminResponse(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException ex) {
            // The order already has another paid payment
            throw new BusinessException(PaymentErrorCode.PAYMENT_STATUS_NOT_EDITABLE, PaymentErrorCode.PAYMENT_STATUS_NOT_EDITABLE.getDefaultMessage(), ex);
        }
    }

    private PaymentMethod methodOf(Payment payment) {
        return paymentMethodRepository.findById(payment.getPaymentMethodId())
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
    }
}
