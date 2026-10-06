package com.ecm.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @NotNull UUID paymentMethodId,
        @NotNull @Positive Long amount,
        // Null for payments created manually by staff; the saga always supplies one.
        String idempotencyKey
) {
}
