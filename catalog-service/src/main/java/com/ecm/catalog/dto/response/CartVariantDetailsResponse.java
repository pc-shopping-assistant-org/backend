package com.ecm.catalog.dto.response;

import java.util.UUID;

/**
 * What the Order Service needs to show or price a variant. {@code variantLabel} is the options joined into one line,
 * for example "Storage: 256GB, Color: Blue"; {@code sellable} is true only when both the variant and its product are ACTIVE.
 */
public record CartVariantDetailsResponse(
        UUID id,
        UUID productId,
        UUID categoryId,
        String productName,
        String sku,
        String model,
        String variantLabel,
        Long price,
        Integer quantity,
        String status,
        String mainImageUrl,
        boolean sellable
) {
}
