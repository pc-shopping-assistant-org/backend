package com.ecm.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code status} is the payment gateway's own vocabulary, normalized to "PAID"/"FAILED" in {@code PaymentService}.
 */
public record PaymentWebhookRequest(
        @NotBlank String status,
        String providerTransactionCode
) {
}
