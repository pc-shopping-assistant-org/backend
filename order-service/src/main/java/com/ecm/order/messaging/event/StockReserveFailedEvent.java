package com.ecm.order.messaging.event;

import java.util.UUID;

/** Consumed from catalog-service stock.reserve-failed topic. Own copy per service, see service-structure.md Rule 4. */
public record StockReserveFailedEvent(
        UUID eventId,
        UUID orderId,
        String reason
) {
}
