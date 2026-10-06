package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.CatalogServiceClient;
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
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
    private static final UUID VARIANT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_VARIANT = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private CatalogServiceClient catalogClient;
    private CartService service;
    private final Cart cart = Cart.builder().id(CART_ID).customerId(CUSTOMER).build();

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        catalogClient = mock(CatalogServiceClient.class);
        service = new CartService(cartRepository, cartItemRepository, catalogClient);
        when(cartRepository.lockByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(cartRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndVariantId(any(), any())).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of());
    }

    private static CartVariantDetailsResponse variant(UUID id, long price, int stock, boolean sellable) {
        return new CartVariantDetailsResponse(id, UUID.randomUUID(), "Product", "SKU-" + id.toString().substring(34), "M", price,
                stock, sellable ? "ACTIVE" : "INACTIVE", "https://img/x.png", sellable);
    }

    private void catalogReturns(CartVariantDetailsResponse... variants) {
        when(catalogClient.getCartVariantDetails(anyList())).thenReturn(ApiResponse.success(List.of(variants)));
    }

    private static CartItem line(UUID variantId, int quantity) {
        return CartItem.builder().cartId(CART_ID).variantId(variantId).quantity(quantity).build();
    }

    // ---- UC-CART-001 add ----

    @Test
    void addCreatesTheCartIfNeededAndStoresTheLine() {
        catalogReturns(variant(VARIANT, 1_000_000, 10, true));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(line(VARIANT, 2)));

        CartResponse response = service.addItem(CUSTOMER, new AddToCartRequest(VARIANT, 2));

        verify(cartRepository).insertIfAbsent(CUSTOMER);
        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(saved.capture());
        assertEquals(2, saved.getValue().getQuantity());
        assertEquals(2_000_000L, response.subtotalAmount());
        assertEquals(2, response.totalItems());
    }

    @Test
    void addingAVariantAlreadyInTheCartIncreasesItsQuantity() {
        catalogReturns(variant(VARIANT, 1000, 10, true));
        CartItem existing = line(VARIANT, 3);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(existing));

        service.addItem(CUSTOMER, new AddToCartRequest(VARIANT, 4));

        assertEquals(7, existing.getQuantity());
        verify(cartItemRepository).save(existing);
    }

    @Test
    void addRejectsAQuantityThatTogetherWithTheCartExceedsTheStock() {
        catalogReturns(variant(VARIANT, 1000, 5, true));
        CartItem existing = line(VARIANT, 3);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addItem(CUSTOMER, new AddToCartRequest(VARIANT, 3)));

        assertEquals(OrderErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
        assertEquals(3, existing.getQuantity());
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addRejectsAVariantThatIsNotOnSaleOrUnknownBeforeTouchingTheCart() {
        for (CartVariantDetailsResponse[] answer : new CartVariantDetailsResponse[][]{
                {variant(VARIANT, 1000, 5, false)}, {}}) {
            catalogReturns(answer);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.addItem(CUSTOMER, new AddToCartRequest(VARIANT, 1)));

            assertEquals(OrderErrorCode.VARIANT_NOT_AVAILABLE, ex.getErrorCode());
        }
        verify(cartRepository, never()).insertIfAbsent(any());
    }

    @Test
    void aCatalogFailureIsReportedAsAnExternalServiceFailure() {
        when(catalogClient.getCartVariantDetails(anyList())).thenThrow(mock(FeignException.class));

        assertThrows(ExternalServiceException.class, () -> service.addItem(CUSTOMER, new AddToCartRequest(VARIANT, 1)));
    }

    // ---- UC-CART-002 update ----

    @Test
    void updateReplacesTheQuantity() {
        catalogReturns(variant(VARIANT, 1000, 10, true));
        CartItem existing = line(VARIANT, 3);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(existing));

        service.updateItem(CUSTOMER, VARIANT, new UpdateCartItemRequest(8));

        assertEquals(8, existing.getQuantity());
        verify(cartItemRepository).save(existing);
    }

    @Test
    void updateRejectsAQuantityOverTheStockAndLeavesTheLineAlone() {
        catalogReturns(variant(VARIANT, 1000, 5, true));
        CartItem existing = line(VARIANT, 3);
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateItem(CUSTOMER, VARIANT, new UpdateCartItemRequest(6)));

        assertEquals(OrderErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
        assertEquals(3, existing.getQuantity());
    }

    @Test
    void updateOfALineThatIsNotInTheCartOrWithoutACartIsNotFound() {
        assertEquals(OrderErrorCode.CART_ITEM_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.updateItem(CUSTOMER, VARIANT, new UpdateCartItemRequest(1))).getErrorCode());

        UUID stranger = UUID.randomUUID();
        assertEquals(OrderErrorCode.CART_ITEM_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.updateItem(stranger, VARIANT, new UpdateCartItemRequest(1))).getErrorCode());
    }

    @Test
    void updateOfAVariantThatWentOffSaleIsRejected() {
        catalogReturns(variant(VARIANT, 1000, 5, false));
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(line(VARIANT, 1)));

        assertEquals(OrderErrorCode.VARIANT_NOT_AVAILABLE, assertThrows(BusinessException.class,
                () -> service.updateItem(CUSTOMER, VARIANT, new UpdateCartItemRequest(2))).getErrorCode());
    }

    // ---- UC-CART-003 remove ----

    @Test
    void removeDeletesTheLineEvenWhenTheVariantIsNoLongerOnSale() {
        when(cartItemRepository.findByCartIdAndVariantId(CART_ID, VARIANT)).thenReturn(Optional.of(line(VARIANT, 1)));

        service.removeItem(CUSTOMER, VARIANT);

        verify(cartItemRepository).deleteItem(CART_ID, VARIANT);
        verify(catalogClient, never()).getCartVariantDetails(anyList());
    }

    @Test
    void removeOfALineThatIsNotInTheCartIsNotFound() {
        assertEquals(OrderErrorCode.CART_ITEM_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.removeItem(CUSTOMER, VARIANT)).getErrorCode());
        verify(cartItemRepository, never()).deleteItem(any(), any());
    }

    // ---- view ----

    @Test
    void getCartOfACustomerWithoutOneIsEmptyAndCreatesNothing() {
        when(cartRepository.findByCustomerId(CUSTOMER)).thenReturn(Optional.empty());

        CartResponse response = service.getCart(CUSTOMER);

        assertTrue(response.items().isEmpty());
        assertEquals(0L, response.subtotalAmount());
        verify(cartRepository, never()).insertIfAbsent(any());
    }

    @Test
    void unavailableLinesStayVisibleButAreLeftOutOfTheTotals() {
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(line(VARIANT, 2), line(OTHER_VARIANT, 5)));
        catalogReturns(variant(VARIANT, 1000, 10, true), variant(OTHER_VARIANT, 9999, 10, false));

        CartResponse response = service.getCart(CUSTOMER);

        assertEquals(2, response.items().size());
        assertEquals(2, response.totalItems());
        assertEquals(2000L, response.subtotalAmount());
        CartItemResponse offSale = response.items().stream().filter(item -> item.productVariantId().equals(OTHER_VARIANT)).findFirst().orElseThrow();
        assertFalse(offSale.available());
    }

    @Test
    void aLineWhoseVariantCatalogNoLongerKnowsIsShownAsUnavailable() {
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(line(VARIANT, 2)));
        catalogReturns();

        CartResponse response = service.getCart(CUSTOMER);

        assertFalse(response.items().getFirst().available());
        assertEquals(0L, response.subtotalAmount());
    }

    @Test
    void clearEmptiesTheCart() {
        service.clearCart(CUSTOMER);

        verify(cartItemRepository).deleteByCartId(CART_ID);
    }
}
