package com.ecm.order.dto.response;

import java.util.UUID;

public record DiscountApplyResponse(
        UUID discountId,
        Long discountAmount
) {
}
