package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.request.UpdateCartItemRequest;
import com.ecm.order.dto.response.CartItemResponse;
import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The shopping cart of a customer: UC-CART-001..003. A customer has exactly one cart, created on first use. */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogVariantLookup variantLookup;

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId).map(this::toResponse).orElseGet(() -> emptyCart(null));
    }

    @Transactional
    public CartResponse addItem(UUID customerId, AddToCartRequest request) {
        // 1. The variant must be on sale
        CartVariantDetailsResponse variant = requireSellable(request.productVariantId());

        // 2. Find or create the customer cart, locked so concurrent adds to the same cart queue up
        Cart cart = lockOrCreateCart(customerId);

        // 3. Add to the quantity already in the cart, which together must not exceed the stock
        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.id())
                .orElseGet(() -> CartItem.builder().cartId(cart.getId()).variantId(variant.id()).quantity(0).build());
        int quantity = item.getQuantity() + request.quantity();
        requireStock(variant, quantity);
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID customerId, UUID variantId, UpdateCartItemRequest request) {
        // 1. The line must be in the cart of the current customer
        Cart cart = cartRepository.lockByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));
        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));

        // 2. The new quantity replaces the old one, within the stock
        CartVariantDetailsResponse variant = requireSellable(variantId);
        requireStock(variant, request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID customerId, UUID variantId) {
        Cart cart = cartRepository.lockByCustomerId(customerId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));
        if (cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId).isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }
        cartItemRepository.deleteItem(cart.getId(), variantId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse clearCart(UUID customerId) {
        return cartRepository.lockByCustomerId(customerId).map(cart -> {
            cartItemRepository.deleteByCartId(cart.getId());
            return emptyCart(cart.getId());
        }).orElseGet(() -> emptyCart(null));
    }

    private Cart lockOrCreateCart(UUID customerId) {
        cartRepository.insertIfAbsent(customerId);
        return cartRepository.lockByCustomerId(customerId)
                .orElseThrow(() -> new IllegalStateException("Cart of customer " + customerId + " vanished after being created"));
    }

    private CartVariantDetailsResponse requireSellable(UUID variantId) {
        CartVariantDetailsResponse variant = variantLookup.details(List.of(variantId)).stream().findFirst().orElse(null);
        if (variant == null || !variant.sellable()) {
            throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE);
        }
        return variant;
    }

    private static void requireStock(CartVariantDetailsResponse variant, int quantity) {
        if (variant.quantity() == null || quantity > variant.quantity()) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
        }
    }

    /** Current price, name and image come from the Catalog Service; the cart only stores variant and quantity. */
    private CartResponse toResponse(Cart cart) {
        // 1. Load the lines and their variants, one call to the Catalog Service for all of them
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            return emptyCart(cart.getId());
        }
        Map<UUID, CartVariantDetailsResponse> variants = variantLookup.details(cartItems.stream().map(CartItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(CartVariantDetailsResponse::id, Function.identity()));

        // 2. Build the lines; only the lines on sale count towards the totals
        List<CartItemResponse> items = cartItems.stream().map(item -> toItemResponse(item, variants.get(item.getVariantId())))
                .sorted(Comparator.comparing(item -> item.sku() == null ? "" : item.sku())).toList();
        int totalItems = items.stream().filter(CartItemResponse::available).mapToInt(CartItemResponse::quantity).sum();
        long subtotal = items.stream().filter(CartItemResponse::available).mapToLong(CartItemResponse::subtotal).sum();
        return new CartResponse(cart.getId(), items, totalItems, subtotal);
    }

    private CartItemResponse toItemResponse(CartItem item, CartVariantDetailsResponse variant) {
        if (variant == null) {
            return new CartItemResponse(item.getVariantId(), null, null, null, null, null, null, item.getQuantity(), 0L, null, false);
        }
        long lineTotal = variant.price() * item.getQuantity();
        return new CartItemResponse(variant.id(), variant.productId(), variant.productName(), variant.sku(), variant.model(),
                variant.mainImageUrl(), variant.price(), item.getQuantity(), lineTotal, variant.quantity(), variant.sellable());
    }

    private CartResponse emptyCart(UUID cartId) {
        return new CartResponse(cartId, List.of(), 0, 0L);
    }
}
