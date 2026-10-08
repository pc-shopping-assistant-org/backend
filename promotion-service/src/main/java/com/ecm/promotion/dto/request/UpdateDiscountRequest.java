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

/** A full replacement of the discount. An omitted {@code code} keeps the current one; a blank one removes it. */
public record UpdateDiscountRequest(
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
