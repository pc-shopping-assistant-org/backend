package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.request.UpdateCartItemRequest;
import com.ecm.order.dto.response.CartItemResponse;
import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.CartStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int MAX_SESSION_TOKEN_LENGTH = 255;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogServiceClient catalogServiceClient;

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID accountId, String sessionToken) {
        validateOwner(accountId, sessionToken, false);
        return findCart(accountId, normalizeSession(sessionToken), false).map(this::toResponse)
                .orElseGet(() -> emptyCart(null));
    }

    @Transactional
    public CartResponse addItem(UUID accountId, String sessionToken, AddToCartRequest request) {
        validateOwner(accountId, sessionToken, true);
        String normalizedSession = normalizeSession(sessionToken);
        Cart cart = getOrCreateCart(accountId, normalizedSession);
        ProductVariantResponse variant = findSellableVariant(request.productVariantId());
        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.id())
                .orElseGet(() -> CartItem.builder().cartId(cart.getId()).variantId(variant.id()).quantity(0).build());
        int requestedQuantity = addExact(item.getQuantity(), request.quantity());
        validateStock(variant, requestedQuantity);
        item.setQuantity(requestedQuantity);
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(UUID accountId, String sessionToken, UUID variantId, UpdateCartItemRequest request) {
        validateOwner(accountId, sessionToken, true);
        Cart cart = requireActiveCart(accountId, normalizeSession(sessionToken), true);
        CartItem item = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", variantId));
        ProductVariantResponse variant = findSellableVariant(variantId);
        validateStock(variant, request.quantity());
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(UUID accountId, String sessionToken, UUID variantId) {
        validateOwner(accountId, sessionToken, true);
        Cart cart = requireActiveCart(accountId, normalizeSession(sessionToken), true);
        if (cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId).isEmpty()) {
            throw new ResourceNotFoundException("CartItem", variantId);
        }
        cartItemRepository.deleteItem(cart.getId(), variantId);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse clearCart(UUID accountId, String sessionToken) {
        validateOwner(accountId, sessionToken, true);
        return findCart(accountId, normalizeSession(sessionToken), true).map(cart -> {
            cartItemRepository.deleteByCartId(cart.getId());
            return emptyCart(cart.getId());
        }).orElseGet(() -> emptyCart(null));
    }

    @Transactional
    public CartResponse mergeGuestCart(UUID accountId, String sessionToken) {
        if (accountId == null) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        String normalizedSession = normalizeSession(sessionToken);
        if (normalizedSession == null) {
            throw new BusinessException(OrderErrorCode.CART_SESSION_REQUIRED);
        }
        Cart accountCart = getOrCreateCart(accountId, null);
        Cart guestCart = cartRepository.lockActiveBySessionToken(normalizedSession, CartStatus.ACTIVE).orElse(null);
        if (guestCart == null || Objects.equals(guestCart.getId(), accountCart.getId())) {
            return toResponse(accountCart);
        }
        List<CartItem> guestItems = cartItemRepository.findByCartId(guestCart.getId());
        for (CartItem guestItem : guestItems) {
            ProductVariantResponse variant = findSellableVariant(guestItem.getVariantId());
            CartItem accountItem = cartItemRepository.findByCartIdAndVariantId(accountCart.getId(), variant.id())
                    .orElseGet(() -> CartItem.builder().cartId(accountCart.getId()).variantId(variant.id()).quantity(0).build());
            int mergedQuantity = addExact(accountItem.getQuantity(), guestItem.getQuantity());
            validateStock(variant, mergedQuantity);
            accountItem.setQuantity(mergedQuantity);
            cartItemRepository.save(accountItem);
        }
        guestCart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(guestCart);
        return toResponse(accountCart);
    }

    private Cart getOrCreateCart(UUID accountId, String sessionToken) {
        return findCart(accountId, sessionToken, true).orElseGet(() -> {
            Cart cart = Cart.builder().customerId(accountId).sessionToken(sessionToken).status(CartStatus.ACTIVE).build();
            try {
                return cartRepository.saveAndFlush(cart);
            } catch (DataIntegrityViolationException ex) {
                return findCart(accountId, sessionToken, true)
                        .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_NOT_ACTIVE,
                                "An active cart already exists; retry the request", ex));
            }
        });
    }

    private java.util.Optional<Cart> findCart(UUID accountId, String sessionToken, boolean lock) {
        return accountId != null
                ? lock ? cartRepository.lockActiveByCustomerId(accountId, CartStatus.ACTIVE)
                        : cartRepository.findActiveByCustomerId(accountId, CartStatus.ACTIVE)
                : lock ? cartRepository.lockActiveBySessionToken(sessionToken, CartStatus.ACTIVE)
                        : cartRepository.findActiveBySessionToken(sessionToken, CartStatus.ACTIVE);
    }

    private Cart requireActiveCart(UUID accountId, String sessionToken, boolean lock) {
        return findCart(accountId, sessionToken, lock)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", accountId != null ? accountId : sessionToken));
    }

    private ProductVariantResponse findSellableVariant(UUID variantId) {
        try {
            ProductVariantResponse variant = catalogServiceClient.getVariant(variantId).getData();
            if (variant == null || !ACTIVE_STATUS.equalsIgnoreCase(variant.status())) {
                throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE);
            }
            return new ProductVariantResponse(variant.id(), variant.productId(), variant.price(), variant.quantity(),
                    variant.sku(), variant.model(), variant.status(), null, null, variant.images());
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException("ProductVariant", variantId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("catalog-service", ex);
        }
    }


    private void validateStock(ProductVariantResponse variant, int quantity) {
        if (variant.quantity() == null || quantity > variant.quantity()) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private int addExact(int currentQuantity, int quantityToAdd) {
        try {
            return Math.addExact(currentQuantity, quantityToAdd);
        } catch (ArithmeticException ex) {
            throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            return emptyCart(cart.getId());
        }
        List<CartVariantDetailsResponse> details;
        try {
            details = catalogServiceClient.getCartVariantDetails(cartItems.stream().map(CartItem::getVariantId).distinct().toList()).getData();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("catalog-service", ex);
        }
        java.util.Map<UUID, CartVariantDetailsResponse> byId = details == null ? java.util.Map.of() : details.stream()
                .collect(java.util.stream.Collectors.toMap(CartVariantDetailsResponse::id, item -> item));
        List<CartItemResponse> items = new ArrayList<>();
        int totalItems = 0;
        long subtotal = 0L;
        for (CartItem item : cartItems) {
            CartVariantDetailsResponse variant = byId.get(item.getVariantId());
            if (variant == null || !ACTIVE_STATUS.equalsIgnoreCase(variant.status())) {
                throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE);
            }
            long lineTotal;
            try {
                lineTotal = Math.multiplyExact(variant.price(), (long) item.getQuantity());
                subtotal = Math.addExact(subtotal, lineTotal);
                totalItems = Math.addExact(totalItems, item.getQuantity());
            } catch (ArithmeticException ex) {
                throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
            }
            items.add(new CartItemResponse(variant.id(), variant.productId(), variant.productName(), variant.sku(),
                    variant.model(), variant.mainImageUrl(), variant.price(), item.getQuantity(), lineTotal, variant.quantity()));
        }
        items.sort(Comparator.comparing(item -> item.sku() == null ? "" : item.sku()));
        return new CartResponse(cart.getId(), List.copyOf(items), totalItems, subtotal);
    }

    private CartResponse emptyCart(UUID cartId) {
        return new CartResponse(cartId, List.of(), 0, 0L);
    }

    private void validateOwner(UUID accountId, String sessionToken, boolean required) {
        boolean hasSession = normalizeSession(sessionToken) != null;
        if (accountId != null && hasSession) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        if (required && accountId == null && !hasSession) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
    }

    private String normalizeSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return null;
        }
        String normalized = sessionToken.trim();
        if (normalized.length() > MAX_SESSION_TOKEN_LENGTH) {
            throw new BusinessException(OrderErrorCode.CART_SESSION_REQUIRED);
        }
        return normalized;
    }
}
