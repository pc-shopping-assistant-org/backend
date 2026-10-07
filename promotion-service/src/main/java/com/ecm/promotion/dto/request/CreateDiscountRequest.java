package com.ecm.promotion.dto.request;

import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateDiscountRequest(
        @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String title,
        @NotNull DiscountType discountType,
        @Positive int value,
        @NotNull ApplicationScope applicationScope,
        @PositiveOrZero Long minOrderAmount,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        String description,
        Set<@NotNull UUID> categoryIds
) {
}
