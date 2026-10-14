package com.ecm.order.dto.response;

import java.util.UUID;

/** A cart line. An unavailable line (hidden, deleted or out of sale) stays visible but is left out of the totals. */
public record CartItemResponse(
        UUID productVariantId,
        UUID productId,
        UUID categoryId,
        String productName,
        String sku,
        String model,
        String variantLabel,
        String imageUrl,
        Long price,
        Integer quantity,
        Long subtotal,
        Integer stockQuantity,
        boolean available
) {
}
