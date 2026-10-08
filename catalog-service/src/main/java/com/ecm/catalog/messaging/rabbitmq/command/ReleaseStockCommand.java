package com.ecm.catalog.messaging.rabbitmq.command;

import java.util.List;
import java.util.UUID;

/** Consumed from order-service: give back the stock reserved for an order, if any. Own copy per service, see service-structure.md Rule 4. */
public record ReleaseStockCommand(
        UUID commandId,
        UUID orderId,
        List<StockItem> items
) {
}
