package com.ecm.order.dto.response;

import java.util.UUID;

public record CartItemResponse(
        UUID productVariantId,
        UUID productId,
        String productName,
        String sku,
        String model,
        String imageUrl,
        Long listPrice,
        Integer quantity,
        Long subtotal,
        Integer stockQuantity
) {
}
