package com.ecm.catalog.messaging.rabbitmq.command;

import java.util.UUID;

/** Consumed from order-service. Own copy per service — see service-structure.md Rule 4. */
public record ReleaseStockCommand(
        UUID commandId,
        UUID orderId,
        UUID productVariantId,
        int quantity
) {
}
