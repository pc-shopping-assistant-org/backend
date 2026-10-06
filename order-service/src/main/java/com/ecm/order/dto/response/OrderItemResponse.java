package com.ecm.order.dto.response;

import java.util.UUID;

/** An order line exactly as it was when the order was placed. {@code lineTotal} is the line after its own discount. */
public record OrderItemResponse(
        UUID id,
        UUID productVariantId,
        String productName,
        String sku,
        String variantLabel,
        int quantity,
        Long unitPrice,
        Long discountAmount,
        Long lineTotal
) {
}
