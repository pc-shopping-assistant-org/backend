package com.ecm.order.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddToCartRequest(
        @NotNull UUID productVariantId,
        @NotNull @Min(1) @Max(9999) Integer quantity
) {
}
