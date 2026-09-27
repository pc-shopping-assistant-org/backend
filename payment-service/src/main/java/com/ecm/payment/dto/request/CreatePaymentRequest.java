package com.ecm.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID paymentMethodId,
        @NotNull @Positive Long amount
) {
}
