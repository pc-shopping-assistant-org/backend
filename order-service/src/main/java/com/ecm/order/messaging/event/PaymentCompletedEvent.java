package com.ecm.order.messaging.event;

import java.util.UUID;

/** Consumed from payment-service's {@code payment.completed} topic. Own copy per service — see service-structure.md Rule 4. */
public record PaymentCompletedEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId
) {
}
