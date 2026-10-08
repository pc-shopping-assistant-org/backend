package com.ecm.order.messaging.event;

import java.util.UUID;

/** Published to the order.cancelled topic so that other services can undo what they did for the order, for example cancel its pending payment. */
public record OrderCancelledEvent(
        UUID eventId,
        UUID orderId,
        String reason
) {
}
