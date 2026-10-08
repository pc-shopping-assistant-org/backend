package com.ecm.payment.dto.response;

import com.ecm.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/** A payment as the shop sees it, with the customer, the gateway transaction code and who last changed it. */
public record AdminPaymentResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        UUID paymentMethodId,
        Long amount,
        PaymentStatus status,
        String providerTransactionCode,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt,
        UUID updatedBy
) {
}
