package com.ecm.order.dto.response;

import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productVariantId,
        int quantity,
        Long unitPrice, UUID itemDiscountId, Long itemDiscount) {
}
