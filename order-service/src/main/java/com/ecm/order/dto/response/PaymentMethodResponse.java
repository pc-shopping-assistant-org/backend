package com.ecm.order.dto.response;

import java.util.UUID;

public record PaymentMethodResponse(UUID id, String code, String name) {
}
