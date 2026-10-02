package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.CartStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CatalogServiceClient catalogServiceClient;
    @InjectMocks
    private CartService cartService;

    @Test
    void addItemCreatesGuestCartForSessionOwner() {
        UUID cartId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).sessionToken("guest-session").status(CartStatus.ACTIVE).build();
        ProductVariantResponse variant = new ProductVariantResponse(variantId, UUID.randomUUID(), 1200L,
                5, "sku-1", "model-1", "ACTIVE", null, null, java.util.List.of());
        when(cartRepository.lockActiveBySessionToken("guest-session", CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class))).thenReturn(cart);
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success("ok", variant));

        when(cartItemRepository.findByCartIdAndVariantId(cartId, variantId)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(java.util.List.of());

        var response = cartService.addItem(null, "guest-session", new AddToCartRequest(variantId, 2));

        assertEquals(cartId, response.id());
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItemRejectsProvidingBothOwners() {
        assertThrows(BusinessException.class, () -> cartService.addItem(
                UUID.randomUUID(), "guest-session", new AddToCartRequest(UUID.randomUUID(), 1)));
        verify(cartRepository, never()).saveAndFlush(any(Cart.class));
    }
}
