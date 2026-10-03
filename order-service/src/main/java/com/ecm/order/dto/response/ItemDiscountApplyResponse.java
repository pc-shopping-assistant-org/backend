package com.ecm.order.dto.response;

import java.util.UUID;

public record ItemDiscountApplyResponse(
        UUID productVariantId,
        UUID discountId,
        Long discountAmount
) {
}
