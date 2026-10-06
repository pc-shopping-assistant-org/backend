package com.ecm.catalog.messaging.event;

import java.util.UUID;

/** Published to the stock.released topic once the stock of an order has been given back. */
public record StockReleasedEvent(
        UUID eventId,
        UUID orderId
) {
}
