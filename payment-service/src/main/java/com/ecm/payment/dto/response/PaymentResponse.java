package com.ecm.payment.dto.response;

import com.ecm.payment.entity.PaymentStatus;

import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentStatus status
) {
}
