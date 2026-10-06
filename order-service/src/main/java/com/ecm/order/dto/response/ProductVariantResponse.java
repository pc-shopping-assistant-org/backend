package com.ecm.order.dto.response;

import java.util.List;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        UUID productId,
        Long price,
        Integer quantity,
        String sku,
        String model,
        String status,
        String productName,
        String mainImageUrl,
        List<ProductImageResponse> images
) {
}
