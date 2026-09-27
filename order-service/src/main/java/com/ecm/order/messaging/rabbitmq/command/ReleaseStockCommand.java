package com.ecm.order.messaging.rabbitmq.command;

import java.util.UUID;

/**
 * Published by order-service, consumed by catalog-service. Own copy per service — see service-structure.md Rule 4.
 */
public record ReleaseStockCommand(
        UUID commandId,
        UUID orderId,
        UUID productVariantId,
        int quantity
) {
}
