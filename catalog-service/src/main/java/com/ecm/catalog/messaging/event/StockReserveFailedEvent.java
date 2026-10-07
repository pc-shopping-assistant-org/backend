package com.ecm.catalog.messaging.event;

import java.util.UUID;

/** Published to order-service via the stock.reserve-failed topic when any line of the order cannot be reserved. Own copy per service, see service-structure.md Rule 4. */
public record StockReserveFailedEvent(
        UUID eventId,
        UUID orderId,
        String reason
) {
}
