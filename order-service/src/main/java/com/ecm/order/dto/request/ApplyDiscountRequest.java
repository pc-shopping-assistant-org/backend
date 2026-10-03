package com.ecm.order.dto.request;

import java.util.List;
import java.util.UUID;

public record ApplyDiscountRequest(
        String code,
        Long orderAmount,
        List<DiscountCartItemRequest> items, String checkoutKey
) {
    public record DiscountCartItemRequest(UUID productVariantId, int quantity, long unitPrice, UUID categoryId) {
    }
}
