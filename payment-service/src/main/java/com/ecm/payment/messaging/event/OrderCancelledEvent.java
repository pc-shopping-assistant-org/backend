package com.ecm.payment.messaging.event;

import java.util.UUID;

/** Consumed from order-service order.cancelled topic. Own copy per service, see service-structure.md Rule 4. */
public record OrderCancelledEvent(
        UUID eventId,
        UUID orderId,
        String reason
) {
}
