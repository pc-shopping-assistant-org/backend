package com.ecm.order.messaging.event;

import java.util.UUID;

/** Consumed from catalog-service stock.reserved topic. Own copy per service, see service-structure.md Rule 4. */
public record StockReservedEvent(
        UUID eventId,
        UUID orderId
) {
}
