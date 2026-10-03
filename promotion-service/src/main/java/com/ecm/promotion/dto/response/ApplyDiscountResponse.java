package com.ecm.promotion.dto.response;

import java.util.List;
import java.util.UUID;

public record ApplyDiscountResponse(
        UUID discountId,
        long discountAmount,
        UUID orderDiscountId,
        long orderDiscountAmount,
        List<ItemDiscountResponse> itemDiscounts
) {
    public record ItemDiscountResponse(UUID productVariantId, UUID discountId, long discountAmount) {
    }
}
