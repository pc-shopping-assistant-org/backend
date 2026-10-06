package com.ecm.order.service;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.request.AddToCartRequest;
import com.ecm.order.dto.response.CartResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.CartStatus;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock CatalogServiceClient catalogServiceClient;
    @InjectMocks CartService cartService;

    @Test
    void addsItemAndReturnsCatalogNameImageWithoutPerItemProductCalls() {
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).sessionToken("guest-session").status(CartStatus.ACTIVE).build();
        ProductVariantResponse variant = new ProductVariantResponse(variantId, productId, 125L, 8,
                "SKU-1", "Model", "ACTIVE", null, null, List.of());
        when(cartRepository.lockActiveBySessionToken("guest-session", CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class))).thenReturn(cart);
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success(variant));
        when(catalogServiceClient.getCartVariantDetails(List.of(variantId))).thenReturn(ApiResponse.success(List.of(new CartVariantDetailsResponse(variantId, productId, "RAM", "SKU-1", "Model", 125L, 8, "ACTIVE", "/image.jpg"))));
        when(cartItemRepository.findByCartIdAndVariantId(cartId, variantId)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(CartItem.builder()
                .cartId(cartId).variantId(variantId).quantity(2).build()));

        CartResponse response = cartService.addItem(null, "guest-session", new AddToCartRequest(variantId, 2));

        assertEquals(250L, response.subtotalAmount());
        assertEquals("RAM", response.items().getFirst().productName());
        assertEquals("/image.jpg", response.items().getFirst().imageUrl());
        verify(catalogServiceClient).getVariant(variantId);
        verify(catalogServiceClient).getCartVariantDetails(List.of(variantId));
    }

    @Test
    void rejectsCartWithoutVerifiedOwnerOrGuestSession() {
        assertThrows(RuntimeException.class, () -> cartService.addItem(null, null,
                new AddToCartRequest(UUID.randomUUID(), 1)));
        verifyNoInteractions(cartRepository, catalogServiceClient);
    }
}
