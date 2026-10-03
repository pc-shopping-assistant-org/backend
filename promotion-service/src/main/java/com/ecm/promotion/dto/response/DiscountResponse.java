package com.ecm.promotion.dto.response;

import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.entity.DiscountType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DiscountResponse(
        UUID id,
        String code,
        String title,
        DiscountType discountType,
        int value,
        ApplicationScope applicationScope,
        Long minOrderAmount,
        Instant startAt,
        Instant endAt,
        String description,
        DiscountStatus status,
        Set<UUID> categoryIds,
        Set<UUID> variantIds, Long usageLimit
) {
}
