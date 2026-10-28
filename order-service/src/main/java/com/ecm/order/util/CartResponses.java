package com.ecm.order.util;

import com.ecm.order.dto.response.CartItemResponse;
import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.entity.CartItem;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds what the customer sees of a cart from its stored lines and the current data of their variants. */
public final class CartResponses {

    private CartResponses() {
    }

    public static CartResponse empty(UUID cartId) {
        return new CartResponse(cartId, List.of(), 0, 0L);
    }

    /** Lines sorted by SKU; only the lines on sale count towards the totals. */
    public static CartResponse of(UUID cartId, List<CartItem> cartItems, Map<UUID, CartVariantDetailsResponse> variants) {
        List<CartItemResponse> items = cartItems.stream().map(item -> line(item, variants.get(item.getVariantId())))
                .sorted(Comparator.comparing(item -> item.sku() == null ? "" : item.sku())).toList();
        int totalItems = items.stream().filter(CartItemResponse::available).mapToInt(CartItemResponse::quantity).sum();
        long subtotal = items.stream().filter(CartItemResponse::available).mapToLong(CartItemResponse::subtotal).sum();
        return new CartResponse(cartId, items, totalItems, subtotal);
    }

    /** A line whose variant is gone from the catalog stays visible, unavailable and worth nothing. */
    private static CartItemResponse line(CartItem item, CartVariantDetailsResponse variant) {
        if (variant == null) {
            return new CartItemResponse(item.getVariantId(), null, null, null, null, null, null, null, null, item.getQuantity(), 0L, null, false);
        }
        long lineTotal = variant.price() * item.getQuantity();
        return new CartItemResponse(variant.id(), variant.productId(), variant.categoryId(), variant.productName(), variant.sku(), variant.model(), variant.variantLabel(),
                variant.mainImageUrl(), variant.price(), item.getQuantity(), lineTotal, variant.quantity(), variant.sellable());
    }
}
