package com.ecm.promotion.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.entity.ApplicationScope;
import com.ecm.promotion.entity.DiscountType;
import com.ecm.promotion.exception.PromotionErrorCode;

import java.time.Instant;
import java.util.Locale;

/** What makes a discount definition valid, and how its code and minimum order are stored. */
public final class DiscountRules {

    private static final int MAX_PERCENT = 100;
    private static final long NO_MINIMUM_ORDER = 0L;

    private DiscountRules() {
    }

    /** A blank code means the discount has none; otherwise codes are stored trimmed and in upper case. */
    public static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public static long minOrderAmount(Long requested) {
        return requested == null ? NO_MINIMUM_ORDER : requested;
    }

    public static void validateDefinition(DiscountType type, int value, Long minOrderAmount, Instant startAt, Instant endAt) {
        boolean validValue = type == DiscountType.PERCENT ? value > 0 && value <= MAX_PERCENT : value > 0;
        if (!validValue || !startAt.isBefore(endAt) || (minOrderAmount != null && minOrderAmount < 0)) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_VALUE);
        }
    }

    /** Only an order-wide discount can be redeemed with a code. */
    public static void validateCode(String code, ApplicationScope scope) {
        if (code != null && scope != ApplicationScope.ORDER) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_CODE);
        }
    }
}
