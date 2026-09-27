package com.ecm.order.dto.request;

import java.util.UUID;

public record CreatePaymentRequest(
        UUID orderId,
        UUID paymentMethodId,
        Long amount
) {
}
