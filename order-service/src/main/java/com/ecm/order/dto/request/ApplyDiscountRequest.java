package com.ecm.order.dto.request;

public record ApplyDiscountRequest(
        String code,
        Long orderAmount
) {
}
