package com.ecm.payment.dto.response;

import com.ecm.payment.entity.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID paymentMethodId,
        Long amount,
        PaymentStatus status,
        Instant paidAt,
        Instant createdAt
) {
}
