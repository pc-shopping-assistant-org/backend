package com.ecm.order.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A payment attempt of an order, as the Payment Service reports it. */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID paymentMethodId,
        Long amount,
        String status,
        Instant paidAt,
        Instant createdAt
) {
}
