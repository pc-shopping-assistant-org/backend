package com.ecm.promotion.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.promotion.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CartLinesTest {

    private static DiscountCartItemRequest line(UUID variant, int quantity, long unitPrice) {
        return new DiscountCartItemRequest(variant, quantity, unitPrice, null);
    }

    @Test
    void theSubtotalAddsQuantityTimesUnitPriceOfEveryLine() {
        assertEquals(2 * 100 + 3 * 50, CartLines.subtotal(List.of(line(UUID.randomUUID(), 2, 100), line(UUID.randomUUID(), 3, 50))));
    }

    @Test
    void aVariantListedTwiceIsRejected() {
        UUID variant = UUID.randomUUID();
        assertThrows(BusinessException.class, () -> CartLines.subtotal(List.of(line(variant, 1, 100), line(variant, 1, 100))));
    }

    @Test
    void anAmountTooBigToAddUpIsRejected() {
        assertThrows(BusinessException.class, () -> CartLines.subtotal(List.of(line(UUID.randomUUID(), Integer.MAX_VALUE, Long.MAX_VALUE))));
    }
}
