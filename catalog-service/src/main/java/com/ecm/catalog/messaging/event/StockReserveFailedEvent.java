package com.ecm.catalog.messaging.event;

import java.util.UUID;

/** Published to order-service via the {@code stock.reserve-failed} topic. Own copy per service — see service-structure.md Rule 4. */
public record StockReserveFailedEvent(
        UUID eventId,
        UUID orderId,
        UUID productVariantId,
        String reason
) {
}
