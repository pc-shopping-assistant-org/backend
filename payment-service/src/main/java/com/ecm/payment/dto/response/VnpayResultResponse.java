package com.ecm.payment.dto.response;

import java.util.UUID;

/**
 * What the customer comes back to after VNPAY: {@code PAID}, {@code FAILED}, or {@code CANCELLED} when they left the
 * VNPAY page without paying (the payment stays pending and can be paid again).
 */
public record VnpayResultResponse(UUID paymentId, UUID orderId, String result, Long amount) {
}
