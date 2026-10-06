package com.ecm.catalog.dto.response;

import java.util.UUID;

/** {@code sellable} is true only when both the variant and its product are ACTIVE. */
public record CartVariantDetailsResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        String model,
        Long price,
        Integer quantity,
        String status,
        String mainImageUrl,
        boolean sellable
) {
}
