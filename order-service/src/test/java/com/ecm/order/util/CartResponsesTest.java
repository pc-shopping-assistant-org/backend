package com.ecm.order.util;

import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.entity.CartItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartResponsesTest {

    private final UUID cartId = UUID.randomUUID();

    @Test
    void emptyCartHasNoLinesAndNoTotals() {
        CartResponse cart = CartResponses.empty(cartId);

        assertTrue(cart.items().isEmpty());
        assertEquals(0, cart.totalItems());
        assertEquals(0L, cart.subtotalAmount());
    }

    @Test
    void linesAreSortedBySkuAndOnlySellableOnesCount() {
        CartItem first = line(2);
        CartItem second = line(3);
        CartItem gone = line(1);
        Map<UUID, CartVariantDetailsResponse> variants = Map.of(
                first.getVariantId(), variant(first.getVariantId(), "B-2", 100L, true),
                second.getVariantId(), variant(second.getVariantId(), "A-1", 50L, false));

        CartResponse cart = CartResponses.of(cartId, List.of(first, second, gone), variants);

        assertEquals(List.of("A-1", "B-2"), cart.items().stream().map(item -> item.sku()).filter(sku -> sku != null).toList());
        assertEquals(2, cart.totalItems());
        assertEquals(200L, cart.subtotalAmount());
        assertFalse(cart.items().stream().filter(item -> item.productVariantId().equals(gone.getVariantId())).findFirst().orElseThrow().available());
    }

    private CartItem line(int quantity) {
        return CartItem.builder().cartId(cartId).variantId(UUID.randomUUID()).quantity(quantity).build();
    }

    private CartVariantDetailsResponse variant(UUID id, String sku, long price, boolean sellable) {
        return new CartVariantDetailsResponse(id, UUID.randomUUID(), UUID.randomUUID(), "Product", sku, null, null, price, 10, "ACTIVE", null, sellable);
    }
}
