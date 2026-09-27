package com.ecm.order.dto.response;

import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String status
) {
}
