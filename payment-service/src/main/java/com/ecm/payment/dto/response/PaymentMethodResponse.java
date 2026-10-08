package com.ecm.payment.dto.response;

import java.util.UUID;

public record PaymentMethodResponse(UUID id, String code, String name) {
}
