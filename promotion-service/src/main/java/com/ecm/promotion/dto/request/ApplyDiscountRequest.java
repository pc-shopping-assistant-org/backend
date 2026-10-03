package com.ecm.promotion.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ApplyDiscountRequest(
        @Size(max = 50) String code,
        @PositiveOrZero long orderAmount,
        @NotEmpty List<@NotNull @jakarta.validation.Valid DiscountCartItemRequest> items, @Size(max = 100) String checkoutKey
) {
    public record DiscountCartItemRequest(
            @NotNull UUID productVariantId,
            @Positive int quantity,
            @PositiveOrZero long unitPrice,
            UUID categoryId
    ) {
    }
}
