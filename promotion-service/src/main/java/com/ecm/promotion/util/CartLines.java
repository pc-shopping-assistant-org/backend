package com.ecm.promotion.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.promotion.exception.PromotionErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads the cart lines a checkout sends to be priced. */
public final class CartLines {

    private CartLines() {
    }

    /** The cost of all lines before any discount; a variant listed twice, or an amount too big to add up, is rejected. */
    public static long subtotal(List<DiscountCartItemRequest> items) {
        Set<UUID> seenVariants = new HashSet<>();
        long subtotal = 0L;
        try {
            for (DiscountCartItemRequest item : items) {
                if (!seenVariants.add(item.productVariantId())) {
                    throw new BusinessException(PromotionErrorCode.DISCOUNT_NOT_APPLICABLE, "Duplicate cart line");
                }
                subtotal = Math.addExact(subtotal, Math.multiplyExact(item.unitPrice(), (long) item.quantity()));
            }
        } catch (ArithmeticException ex) {
            throw new BusinessException(PromotionErrorCode.INVALID_DISCOUNT_VALUE);
        }
        return subtotal;
    }
}
