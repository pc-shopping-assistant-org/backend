package com.ecm.payment.dto.request;

import com.ecm.payment.entity.PaymentStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Every field narrows the search; {@code customerName} is resolved to customers by the Identity Service. */
public record AdminPaymentSearchRequest(
        @Size(max = 100) String transactionCode,
        UUID customerId,
        @Size(max = 100) String customerName,
        UUID orderId,
        PaymentStatus status,
        Instant createdFrom,
        Instant createdTo
) {
}
