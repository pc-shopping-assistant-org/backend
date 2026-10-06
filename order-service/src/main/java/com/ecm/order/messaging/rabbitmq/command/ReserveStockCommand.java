package com.ecm.order.messaging.rabbitmq.command;

import java.util.List;
import java.util.UUID;

/** Published by order-service, consumed by catalog-service: reserve the stock of every line of an order, all or nothing. Own copy per service, see service-structure.md Rule 4. */
public record ReserveStockCommand(
        UUID commandId,
        UUID orderId,
        List<StockItem> items
) {
}
