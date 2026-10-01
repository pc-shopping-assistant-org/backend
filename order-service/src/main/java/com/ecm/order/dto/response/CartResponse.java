package com.ecm.order.dto.response;

import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        List<CartItemResponse> items,
        Integer totalItems,
        Long subtotalAmount
) {
}
