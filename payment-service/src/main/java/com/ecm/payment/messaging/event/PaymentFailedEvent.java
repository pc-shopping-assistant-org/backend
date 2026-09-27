package com.ecm.payment.messaging.event;

import java.util.UUID;

/**
 * Published to order-service via the {@code payment.failed} topic. Own copy per service — see service-structure.md Rule 4.
 */
public record PaymentFailedEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String reason
) {
}
