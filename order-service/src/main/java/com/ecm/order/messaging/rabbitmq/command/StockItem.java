package com.ecm.order.messaging.rabbitmq.command;

import java.util.UUID;

/** One order line of a stock command. Own copy per service, see service-structure.md Rule 4. */
public record StockItem(UUID productVariantId, int quantity) {
}
