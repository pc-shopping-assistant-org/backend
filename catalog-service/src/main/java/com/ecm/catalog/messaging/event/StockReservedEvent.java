package com.ecm.catalog.messaging.event;

import java.util.UUID;

/** Published to order-service via the stock.reserved topic once every line of the order is reserved. Own copy per service, see service-structure.md Rule 4. */
public record StockReservedEvent(
        UUID eventId,
        UUID orderId
) {
}
