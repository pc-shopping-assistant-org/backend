package com.ecm.order.dto.response;

import java.util.UUID;

public record CartVariantDetailsResponse(UUID id, UUID productId, String productName, String sku,
                                         String model, Long listPrice, Integer quantity, String status,
                                         String mainImageUrl) {
}
