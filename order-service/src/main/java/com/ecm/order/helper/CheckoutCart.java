package com.ecm.order.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import com.ecm.order.util.OrderLines;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The cart of a customer as checkout uses it: locked, turned into order lines, and emptied once the order is saved. */
@Component
@RequiredArgsConstructor
public class CheckoutCart {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogVariantLookup variantLookup;

    /** What checkout takes from the cart: the cart itself, the order lines copied from it, and the catalog data of their variants. */
    public record Contents(Cart cart, List<OrderItem> items, Map<UUID, CartVariantDetailsResponse> variants) {
    }

    /** Locks the cart and snapshots its lines; fails when it holds nothing, or a line that cannot be bought now in the stock available. */
    public Contents lockAndSnapshot(UUID customerId) {
        Cart cart = cartRepository.lockByCustomerId(customerId).orElseThrow(() -> new BusinessException(OrderErrorCode.CART_EMPTY));
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_EMPTY);
        }
        Map<UUID, CartVariantDetailsResponse> variants = variantLookup.details(cartItems.stream().map(CartItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(CartVariantDetailsResponse::id, Function.identity()));
        return new Contents(cart, OrderLines.snapshot(cartItems, variants), variants);
    }

    public void empty(Cart cart) {
        cartItemRepository.deleteByCartId(cart.getId());
    }
}
