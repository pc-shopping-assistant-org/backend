package com.ecm.order.dto.request;

import java.util.UUID;

public record CreatePaymentRequest(
        UUID orderId,
        UUID customerId,
        UUID paymentMethodId,
        Long amount,
        String idempotencyKey
) {
}
