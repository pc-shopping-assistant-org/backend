package com.ecm.promotion.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscountTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    private static Discount discount(DiscountType type, int value, DiscountStatus status) {
        return Discount.builder().discountType(type).value(value).status(status).startAt(START).endAt(END).build();
    }

    @Test
    void aPercentageIsRoundedDown() {
        assertEquals(333, discount(DiscountType.PERCENT, 10, DiscountStatus.ACTIVE).reductionOf(3335));
    }

    @Test
    void aFixedSumNeverExceedsTheAmount() {
        assertEquals(500, discount(DiscountType.FIXED, 500, DiscountStatus.ACTIVE).reductionOf(2000));
        assertEquals(300, discount(DiscountType.FIXED, 500, DiscountStatus.ACTIVE).reductionOf(300));
    }

    @Test
    void itIsInEffectOnlyWhenNotLockedAndInsideItsPeriod() {
        Discount active = discount(DiscountType.PERCENT, 10, DiscountStatus.ACTIVE);
        assertTrue(active.isInEffect(START));
        assertFalse(active.isInEffect(START.minusSeconds(1)));
        assertFalse(active.isInEffect(END));
        assertFalse(discount(DiscountType.PERCENT, 10, DiscountStatus.INACTIVE).isInEffect(START.plusSeconds(1)));
        assertFalse(discount(DiscountType.PERCENT, 10, DiscountStatus.DELETED).isInEffect(START.plusSeconds(1)));
    }
}
