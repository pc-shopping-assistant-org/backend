package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.client.IdentityServiceClient;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.AddressResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.dto.response.ItemDiscountApplyResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.PaymentMethodResponse;
import com.ecm.order.entity.Cart;
import com.ecm.order.entity.CartItem;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.ShippingMethod;
import com.ecm.order.entity.ShippingMethodStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.ShippingMethodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheckoutServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VARIANT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER_VARIANT = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SHIPPING = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID COD = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ONLINE = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID ADDRESS = UUID.fromString("00000000-0000-0000-0000-0000000000ad");
    private static final String TOKEN = "Bearer token";

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ShippingMethodRepository shippingMethodRepository;
    private CatalogServiceClient catalogClient;
    private OrderDiscountService discountService;
    private PaymentServiceClient paymentClient;
    private IdentityServiceClient identityClient;
    private InvoiceNumberGenerator invoiceNumbers;
    private OrderStatusService statusService;
    private OrderOutbox outbox;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        shippingMethodRepository = mock(ShippingMethodRepository.class);
        catalogClient = mock(CatalogServiceClient.class);
        discountService = mock(OrderDiscountService.class);
        paymentClient = mock(PaymentServiceClient.class);
        identityClient = mock(IdentityServiceClient.class);
        invoiceNumbers = mock(InvoiceNumberGenerator.class);
        statusService = mock(OrderStatusService.class);
        outbox = mock(OrderOutbox.class);
        service = new CheckoutService(orderRepository, orderItemRepository, cartRepository, cartItemRepository, shippingMethodRepository,
                new CatalogVariantLookup(catalogClient), discountService, paymentClient, identityClient, invoiceNumbers, statusService,
                outbox, Mappers.getMapper(OrderMapper.class));

        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.lockByCustomerId(CUSTOMER)).thenReturn(Optional.of(Cart.builder().id(CART_ID).customerId(CUSTOMER).build()));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(cartLine(VARIANT, 2)));
        when(shippingMethodRepository.findById(SHIPPING)).thenReturn(Optional.of(
                ShippingMethod.builder().id(SHIPPING).fee(30_000L).status(ShippingMethodStatus.ACTIVE).build()));
        when(paymentClient.getPaymentMethods(TOKEN)).thenReturn(ApiResponse.success(List.of(
                new PaymentMethodResponse(COD, "COD", "Cash on delivery"), new PaymentMethodResponse(ONLINE, "ONLINE_CARD", "Card"))));
        when(identityClient.getMyAddresses(TOKEN)).thenReturn(ApiResponse.success(List.of(
                new AddressResponse(ADDRESS, "Saved Name", "0911111111", "1 Saved Street"))));
        when(invoiceNumbers.next()).thenReturn("INV-ABCDEFGH23");
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(call -> {
            Order saved = call.getArgument(0);
            saved.setId(ORDER_ID);
            return saved;
        });
        when(orderItemRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        catalogHas(variant(VARIANT, "SKU-1", 1_000_000L, 5, true));
        discounts(0L, 0L, List.of());
    }

    private static CartItem cartLine(UUID variantId, int quantity) {
        return CartItem.builder().cartId(CART_ID).variantId(variantId).quantity(quantity).build();
    }

    private static CartVariantDetailsResponse variant(UUID id, String sku, long price, int stock, boolean sellable) {
        return new CartVariantDetailsResponse(id, UUID.randomUUID(), CATEGORY, "Product " + sku, sku, "M", "Color: Blue", price, stock,
                sellable ? "ACTIVE" : "INACTIVE", null, sellable);
    }

    private void catalogHas(CartVariantDetailsResponse... variants) {
        when(catalogClient.getCartVariantDetails(anyList())).thenReturn(ApiResponse.success(List.of(variants)));
    }

    private void discounts(long itemDiscount, long orderDiscount, List<ItemDiscountApplyResponse> lines) {
        when(discountService.apply(any(), anyList(), anyLong(), any()))
                .thenReturn(new DiscountApplyResponse(null, itemDiscount + orderDiscount, null, orderDiscount, lines));
    }

    private static CreateOrderRequest request(UUID paymentMethod) {
        return new CreateOrderRequest("key-1", SHIPPING, paymentMethod, null, "Receiver", "0912345678", "2 Typed Street", null, "note");
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(order.capture());
        return order.getValue();
    }

    private static BusinessException rejected(Runnable action) {
        return assertThrows(BusinessException.class, action::run);
    }

    @Test
    void anOnlineOrderStartsPendingPayment() {
        service.placeOrder(request(ONLINE), CUSTOMER, TOKEN);
        assertEquals(OrderStatus.PENDING_PAYMENT, savedOrder().getStatus());
    }

    @Test
    void aCashOnDeliveryOrderStartsPendingConfirmation() {
        service.placeOrder(request(COD), CUSTOMER, TOKEN);
        assertEquals(OrderStatus.PENDING_CONFIRMATION, savedOrder().getStatus());
    }

    @Test
    void theOrderSnapshotsTheLinesAndTheMoneyFollowsTheFormula() {
        discounts(100_000L, 50_000L, List.of(new ItemDiscountApplyResponse(VARIANT, UUID.randomUUID(), 100_000L)));

        OrderDetailResponse response = service.placeOrder(request(COD), CUSTOMER, TOKEN);

        Order order = savedOrder();
        assertEquals(1_900_000L, order.getSubtotalAmount());
        assertEquals(50_000L, order.getDiscountAmount());
        assertEquals(30_000L, order.getShippingFee());
        assertEquals(1_880_000L, order.getTotalAmount());
        assertEquals("INV-ABCDEFGH23", order.getInvoiceNumber());
        assertEquals("Receiver", order.getRecipientName());
        assertEquals(CUSTOMER, order.getCustomerId());
        assertEquals(1, response.items().size());
        assertEquals("Product SKU-1", response.items().getFirst().productName());
        assertEquals("Color: Blue", response.items().getFirst().variantLabel());
        assertEquals(1_900_000L, response.items().getFirst().lineTotal());
    }

    @Test
    void theOrderStatusRowTheCartAndTheStockReservationAreHandledTogether() {
        service.placeOrder(request(COD), CUSTOMER, TOKEN);

        verify(statusService).recordInitial(any(Order.class));
        verify(cartItemRepository).deleteByCartId(CART_ID);
        verify(outbox).reserveStock(any(Order.class), anyList());
    }

    @Test
    void aSavedAddressSuppliesTheRecipient() {
        CreateOrderRequest withAddress = new CreateOrderRequest("key-1", SHIPPING, COD, ADDRESS, null, null, null, null, null);

        service.placeOrder(withAddress, CUSTOMER, TOKEN);

        Order order = savedOrder();
        assertEquals("Saved Name", order.getRecipientName());
        assertEquals("0911111111", order.getRecipientPhone());
        assertEquals("1 Saved Street", order.getDeliveryAddress());
    }

    @Test
    void anAddressThatIsNotTheCustomerOrIsMixedWithTypedDetailsIsRejected() {
        CreateOrderRequest unknown = new CreateOrderRequest("key-1", SHIPPING, COD, UUID.randomUUID(), null, null, null, null, null);
        CreateOrderRequest mixed = new CreateOrderRequest("key-1", SHIPPING, COD, ADDRESS, "Receiver", null, null, null, null);
        CreateOrderRequest nothing = new CreateOrderRequest("key-1", SHIPPING, COD, null, null, null, null, null, null);

        for (CreateOrderRequest bad : List.of(unknown, mixed, nothing)) {
            assertEquals(OrderErrorCode.INVALID_RECIPIENT, rejected(() -> service.placeOrder(bad, CUSTOMER, TOKEN)).getErrorCode());
        }
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void anEmptyOrMissingCartIsRejected() {
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of());
        assertEquals(OrderErrorCode.CART_EMPTY, rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN)).getErrorCode());

        UUID stranger = UUID.randomUUID();
        assertEquals(OrderErrorCode.CART_EMPTY, rejected(() -> service.placeOrder(request(COD), stranger, TOKEN)).getErrorCode());
    }

    @Test
    void aLineThatIsOffSaleOrShortOfStockRejectsTheOrderNamingTheLines() {
        catalogHas(variant(VARIANT, "SKU-1", 1000L, 5, false));
        BusinessException offSale = rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN));
        assertEquals(OrderErrorCode.VARIANT_NOT_AVAILABLE, offSale.getErrorCode());
        assertEquals(true, offSale.getMessage().contains("SKU-1"));

        catalogHas(variant(VARIANT, "SKU-1", 1000L, 1, true));
        BusinessException lowStock = rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN));
        assertEquals(OrderErrorCode.INSUFFICIENT_STOCK, lowStock.getErrorCode());
        assertEquals(true, lowStock.getMessage().contains("SKU-1"));
        verifyNoInteractions(discountService);
    }

    @Test
    void anUnknownShippingOrPaymentMethodIsRejected() {
        when(shippingMethodRepository.findById(SHIPPING)).thenReturn(Optional.of(
                ShippingMethod.builder().id(SHIPPING).fee(1L).status(ShippingMethodStatus.INACTIVE).build()));
        assertEquals(OrderErrorCode.INVALID_SHIPPING_METHOD, rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN)).getErrorCode());

        when(shippingMethodRepository.findById(SHIPPING)).thenReturn(Optional.of(
                ShippingMethod.builder().id(SHIPPING).fee(1L).status(ShippingMethodStatus.ACTIVE).build()));
        assertEquals(OrderErrorCode.INVALID_PAYMENT_METHOD,
                rejected(() -> service.placeOrder(request(UUID.randomUUID()), CUSTOMER, TOKEN)).getErrorCode());
    }

    @Test
    void aRejectedDiscountCodeCreatesNoOrder() {
        when(discountService.apply(any(), anyList(), anyLong(), any())).thenThrow(new BusinessException(OrderErrorCode.DISCOUNT_NOT_APPLICABLE));

        assertEquals(OrderErrorCode.DISCOUNT_NOT_APPLICABLE, rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN)).getErrorCode());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(cartItemRepository, never()).deleteByCartId(any());
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheOrderAlreadyCreatedAndCreatesNothing() {
        Order existing = Order.builder().id(ORDER_ID).customerId(CUSTOMER).status(OrderStatus.PENDING_PAYMENT).build();
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        OrderDetailResponse response = service.placeOrder(request(COD), CUSTOMER, TOKEN);

        assertEquals(ORDER_ID, response.id());
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cartRepository, outbox);
    }

    @Test
    void anIdempotencyKeyOfAnotherCustomerIsRejectedWithoutRevealingTheOrder() {
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(
                Order.builder().id(ORDER_ID).customerId(UUID.randomUUID()).build()));

        rejected(() -> service.placeOrder(request(COD), CUSTOMER, TOKEN));

        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void theItemDiscountIsStoredOnItsLine() {
        UUID discountId = UUID.randomUUID();
        discounts(100_000L, 0L, List.of(new ItemDiscountApplyResponse(VARIANT, discountId, 100_000L)));

        service.placeOrder(request(COD), CUSTOMER, TOKEN);

        ArgumentCaptor<List<OrderItem>> lines = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(lines.capture());
        assertEquals(discountId, lines.getValue().getFirst().getDiscountId());
        assertEquals(100_000L, lines.getValue().getFirst().getDiscountAmount());
        assertNull(savedOrder().getOrderDiscountId());
    }
}
