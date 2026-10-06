package com.ecm.catalog.dto.response;

import java.util.UUID;

public record CartVariantDetailsResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        String model,
        Long price,
        Integer quantity,
        String status,
        String mainImageUrl
) {
}
