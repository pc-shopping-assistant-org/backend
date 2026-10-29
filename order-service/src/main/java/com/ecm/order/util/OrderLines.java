package com.ecm.order.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.order.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.exception.OrderErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns the cart lines of a customer into the lines of an order. */
public final class OrderLines {

    private OrderLines() {
    }

    /** Copies each cart line with the name, SKU, label and price it has now; rejects lines off sale or short of stock. */
    public static List<OrderItem> snapshot(List<CartItem> cartItems, Map<UUID, CartVariantDetailsResponse> variants) {
        List<String> unavailable = new ArrayList<>();
        List<String> lowStock = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            CartVariantDetailsResponse variant = variants.get(cartItem.getVariantId());
            if (variant == null || !variant.sellable()) {
                unavailable.add(variant == null ? cartItem.getVariantId().toString() : variant.sku());
            } else if (variant.quantity() == null || variant.quantity() < cartItem.getQuantity()) {
                lowStock.add(variant.sku() + " (requested " + cartItem.getQuantity() + ", available " + variant.quantity() + ")");
            } else {
                items.add(OrderItem.builder().productVariantId(variant.id()).productName(variant.productName()).sku(variant.sku())
                        .variantLabel(variant.variantLabel()).quantity(cartItem.getQuantity()).unitPrice(variant.price()).discountAmount(0L).build());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE, "No longer available: " + String.join(", ", unavailable));
        }
        if (!lowStock.isEmpty()) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK, "Not enough stock: " + String.join(", ", lowStock));
        }
        return items;
    }

    /** The lines as the Promotion Service wants them, each with the category of its product. */
    public static List<DiscountCartItemRequest> forDiscounts(List<OrderItem> items, Map<UUID, CartVariantDetailsResponse> variants) {
        return items.stream().map(item -> new DiscountCartItemRequest(item.getProductVariantId(), item.getQuantity(),
                item.getUnitPrice(), variants.get(item.getProductVariantId()).categoryId())).toList();
    }

    /** Records on each line the discount that applies to it, and how much it takes off. */
    public static void applyDiscounts(List<OrderItem> items, DiscountApplyResponse discounts) {
        Map<UUID, OrderItem> byVariant = items.stream().collect(Collectors.toMap(OrderItem::getProductVariantId, Function.identity()));
        discounts.itemDiscounts().forEach(itemDiscount -> {
            OrderItem item = byVariant.get(itemDiscount.productVariantId());
            item.setDiscountId(itemDiscount.discountId());
            item.setDiscountAmount(itemDiscount.discountAmount());
        });
    }
}
