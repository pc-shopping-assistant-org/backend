package com.ecm.promotion.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.Discount;
import com.ecm.promotion.entity.DiscountState;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.entity.DiscountType;
import com.ecm.promotion.exception.PromotionErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscountRulesTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void aCodeIsStoredTrimmedAndUpperCaseAndABlankOneIsNone() {
        assertEquals("SALE10", DiscountRules.normalizeCode("  sale10 "));
        assertNull(DiscountRules.normalizeCode("   "));
        assertNull(DiscountRules.normalizeCode(null));
    }

    @Test
    void noMinimumOrderMeansZero() {
        assertEquals(0L, DiscountRules.minOrderAmount(null));
        assertEquals(500_000L, DiscountRules.minOrderAmount(500_000L));
    }

    @Test
    void aPercentOverOneHundredIsRejectedButAFixedAmountMayBeLarger() {
        assertThrows(BusinessException.class, () -> DiscountRules.validateDefinition(DiscountType.PERCENT, 101, null, START, END));
        assertDoesNotThrow(() -> DiscountRules.validateDefinition(DiscountType.FIXED, 200_000, null, START, END));
    }

    @Test
    void aPeriodThatEndsBeforeItStartsOrANegativeMinimumIsRejected() {
        assertThrows(BusinessException.class, () -> DiscountRules.validateDefinition(DiscountType.PERCENT, 10, null, END, START));
        assertThrows(BusinessException.class, () -> DiscountRules.validateDefinition(DiscountType.PERCENT, 10, -1L, START, END));
    }

    @Test
    void onlyAnOrderWideDiscountMayHaveACode() {
        BusinessException ex = assertThrows(BusinessException.class, () -> DiscountRules.validateCode("SALE10", ApplicationScope.CATEGORY));

        assertEquals(PromotionErrorCode.INVALID_DISCOUNT_CODE, ex.getErrorCode());
        assertDoesNotThrow(() -> DiscountRules.validateCode("SALE10", ApplicationScope.ORDER));
        assertDoesNotThrow(() -> DiscountRules.validateCode(null, ApplicationScope.CATEGORY));
    }

    @Test
    void aDiscountIsLockedScheduledRunningOrExpiredByItsStatusAndPeriod() {
        Discount active = Discount.builder().status(DiscountStatus.ACTIVE).startAt(START).endAt(END).build();
        Discount locked = Discount.builder().status(DiscountStatus.INACTIVE).startAt(START).endAt(END).build();

        assertEquals(DiscountState.SCHEDULED, active.stateAt(START.minusSeconds(1)));
        assertEquals(DiscountState.RUNNING, active.stateAt(START));
        assertEquals(DiscountState.EXPIRED, active.stateAt(END));
        assertEquals(DiscountState.LOCKED, locked.stateAt(START.plusSeconds(1)));
    }
}
