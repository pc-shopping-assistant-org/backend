package com.ecm.order.dto.response;

import java.util.UUID;

/**
 * What the Catalog Service reports about a variant. {@code variantLabel} is its options joined into one line;
 * {@code sellable} is true only when both the variant and its product are on sale.
 */
public record CartVariantDetailsResponse(UUID id, UUID productId, UUID categoryId, String productName, String sku,
                                         String model, String variantLabel, Long price, Integer quantity, String status,
                                         String mainImageUrl, boolean sellable) {
}
