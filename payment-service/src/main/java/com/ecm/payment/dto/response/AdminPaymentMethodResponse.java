package com.ecm.payment.dto.response;

import com.ecm.payment.entity.PaymentMethodStatus;

import java.util.UUID;

public record AdminPaymentMethodResponse(UUID id, String code, String name, PaymentMethodStatus status) {
}
