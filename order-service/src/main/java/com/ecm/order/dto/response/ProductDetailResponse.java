package com.ecm.order.dto.response;

import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        String name,
        List<ProductVariantResponse> variants
) {
}
