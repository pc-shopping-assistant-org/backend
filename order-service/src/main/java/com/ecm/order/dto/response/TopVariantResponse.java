package com.ecm.order.dto.response;

import java.util.UUID;

/**
 * A best seller, per variant: the order lines keep no product id, and name, sku and label come from the order snapshot.
 */
public record TopVariantResponse(
        UUID productVariantId,
        String productName,
        String sku,
        String variantLabel,
        long quantitySold,
        long revenue) {
}
