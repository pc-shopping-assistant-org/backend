package com.ecm.order.service;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.entity.*;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ShippingMethodRepository shippingMethodRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock CatalogServiceClient catalogServiceClient;
    @Mock com.ecm.order.client.PromotionServiceClient promotionServiceClient;
    @Mock OrderMapper orderMapper;
    @Mock ObjectMapper objectMapper;
    @InjectMocks OrderService orderService;

    @Test
    void checkoutRepricesCartConvertsCartAndWritesOutbox() throws Exception {
        checkoutWithDiscounts(false);
    }

    @Test
    void checkoutPersistsVoucherAndItemDiscountSnapshots() throws Exception {
        checkoutWithDiscounts(true);
    }

    private void checkoutWithDiscounts(boolean discounted) throws Exception {
        UUID user = UUID.randomUUID(), cartId = UUID.randomUUID(), variantId = UUID.randomUUID();
        UUID shippingId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).customerId(user).status(CartStatus.ACTIVE).build();
        CartItem line = CartItem.builder().cartId(cartId).variantId(variantId).quantity(2).build();
        ProductVariantResponse variant = new ProductVariantResponse(variantId, UUID.randomUUID(), 250L, 5,
                "sku", "model", "ACTIVE", null, null, List.of());
        ShippingMethod shipping = ShippingMethod.builder().id(shippingId).fee(500L).status(ShippingMethodStatus.ACTIVE).build();
        Order order = Order.builder().id(UUID.randomUUID()).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderResponse response = mock(OrderResponse.class);
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(cartRepository.lockActiveByCustomerId(user, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(line));
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success(variant));
        when(shippingMethodRepository.findById(shippingId)).thenReturn(Optional.of(shipping));
        when(catalogServiceClient.getProduct(variant.productId())).thenReturn(ApiResponse.success(new com.ecm.order.dto.response.ProductDetailResponse(variant.productId(), "product", List.of(), UUID.randomUUID())));
        UUID voucherId = UUID.randomUUID(), itemDiscountId = UUID.randomUUID();
        when(promotionServiceClient.apply(any(), anyString())).thenReturn(ApiResponse.success(new com.ecm.order.dto.response.DiscountApplyResponse(discounted ? voucherId : null, discounted ? 150L : 0L,
                        discounted ? voucherId : null, discounted ? 50L : 0L, discounted ? List.of(
                        new com.ecm.order.dto.response.ItemDiscountApplyResponse(variantId, itemDiscountId, 100L)) : List.of())));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderItemRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of());
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        assertSame(response, orderService.createOrder(new CreateOrderRequest("key-1", shippingId, UUID.randomUUID(),
                "Customer", "09123456789", "Address", null, null), auth, null));
        assertEquals(CartStatus.CONVERTED, cart.getStatus());
        verify(orderRepository).save(argThat(saved -> saved.getSubtotalAmount() == 500L
                && saved.getShippingFee() == 500L && saved.getTotalAmount() == (discounted ? 850L : 1000L)
                && saved.getDiscountAmount() == (discounted ? 150L : 0L)
                && java.util.Objects.equals(saved.getOrderDiscountId(), discounted ? voucherId : null)));
        verify(orderItemRepository).saveAll(argThat((java.util.List<com.ecm.order.entity.OrderItem> saved) -> saved.get(0).getItemDiscount() == (discounted ? 100L : 0L)
                && java.util.Objects.equals(saved.getFirst().getItemDiscountId(), discounted ? itemDiscountId : null)));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void rejectsCheckoutWithoutAuthenticatedCustomer() {
        UUID shippingId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest("anonymous-key", shippingId, UUID.randomUUID(),
                "Customer", "09123456789", "Address", null, null);

        assertThrows(RuntimeException.class, () -> orderService.createOrder(request, null, "guest-session"));
        verifyNoInteractions(orderRepository, cartRepository, cartItemRepository, catalogServiceClient);
    }

    private JwtAuthenticationToken customerAuth(UUID user) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .claim("accountId", user.toString()).build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void rejectsCheckoutForUnavailableShippingMethod() {
        UUID user = UUID.randomUUID(), cartId = UUID.randomUUID(), variantId = UUID.randomUUID(), shippingId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).customerId(user).status(CartStatus.ACTIVE).build();
        when(orderRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(cartRepository.lockActiveByCustomerId(user, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(CartItem.builder()
                .cartId(cartId).variantId(variantId).quantity(1).build()));
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success(new ProductVariantResponse(variantId, UUID.randomUUID(), 1L, 5, "sku", "model", "ACTIVE", null, null, List.of())));
        when(shippingMethodRepository.findById(shippingId)).thenReturn(Optional.empty());
        var auth = customerAuth(user);
        assertThrows(RuntimeException.class, () -> orderService.createOrder(new CreateOrderRequest("key-2", shippingId,
                UUID.randomUUID(), "Customer", "09123456789", "Address", null, null), auth, null));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checksIdempotencyKeyAndReturnsExistingOrder() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        Order existing = Order.builder().id(orderId).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderResponse response = mock(OrderResponse.class);
        when(orderRepository.findByIdempotencyKey("duplicate-key")).thenReturn(Optional.of(existing));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(existing), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        assertSame(response, orderService.createOrder(new CreateOrderRequest("duplicate-key", UUID.randomUUID(),
                UUID.randomUUID(), "Customer", "09123456789", "Address", null, null), auth, null));
        verify(cartRepository, never()).lockActiveByCustomerId(any(), any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsCheckoutForEmptyCart() {
        UUID user = UUID.randomUUID(), cartId = UUID.randomUUID(), shippingId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).customerId(user).status(CartStatus.ACTIVE).build();
        when(orderRepository.findByIdempotencyKey("empty-cart")).thenReturn(Optional.empty());
        when(cartRepository.lockActiveByCustomerId(user, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.createOrder(new CreateOrderRequest("empty-cart",
                shippingId, UUID.randomUUID(), "Customer", "09123456789", "Address", null, null), auth, null));
        verify(catalogServiceClient, never()).getVariant(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsCheckoutForInsufficientStock() {
        UUID user = UUID.randomUUID(), cartId = UUID.randomUUID(), variantId = UUID.randomUUID(), shippingId = UUID.randomUUID();
        Cart cart = Cart.builder().id(cartId).customerId(user).status(CartStatus.ACTIVE).build();
        CartItem line = CartItem.builder().cartId(cartId).variantId(variantId).quantity(10).build();
        ProductVariantResponse variant = new ProductVariantResponse(variantId, UUID.randomUUID(), 100L, 3,
                "sku", "model", "ACTIVE", null, null, List.of());
        when(orderRepository.findByIdempotencyKey("low-stock")).thenReturn(Optional.empty());
        when(cartRepository.lockActiveByCustomerId(user, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(line));
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success(variant));
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.createOrder(new CreateOrderRequest("low-stock",
                shippingId, UUID.randomUUID(), "Customer", "09123456789", "Address", null, null), auth, null));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void returnsCustomerOrdersWithPaginationAndStatusFilter() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderResponse response = mock(OrderResponse.class);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByCustomerIdAndStatus(eq(user), eq(OrderStatus.PENDING_CONFIRMATION), any(Pageable.class)))
                .thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        PageResponse<OrderResponse> result = orderService.getCustomerOrders(auth, OrderStatus.PENDING_CONFIRMATION, 0, 20);
        assertEquals(1, result.getContent().size());
        assertSame(response, result.getContent().get(0));
        assertEquals(0, result.getPage());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void returnsCustomerOrderByIdWhenOwned() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderResponse response = mock(OrderResponse.class);
        when(orderRepository.findByIdAndCustomerId(orderId, user)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        assertSame(response, orderService.getCustomerOrder(orderId, auth));
    }

    @Test
    void rejectsGetOrderWhenNotOwned() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndCustomerId(orderId, user)).thenReturn(Optional.empty());
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.getCustomerOrder(orderId, auth));
        verify(orderMapper, never()).toResponse(any(), any());
    }

    @Test
    void cancelsOrderAndWritesReleaseStockCommands() throws Exception {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID(), item1 = UUID.randomUUID(), item2 = UUID.randomUUID();
        UUID variant1 = UUID.randomUUID(), variant2 = UUID.randomUUID();
        Order order = Order.builder().id(orderId).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderItem line1 = OrderItem.builder().id(item1).orderId(orderId).productVariantId(variant1).quantity(2).status(OrderItemStatus.ACTIVE).build();
        OrderItem line2 = OrderItem.builder().id(item2).orderId(orderId).productVariantId(variant2).quantity(1).status(OrderItemStatus.ACTIVE).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(line1, line2));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));
        OrderResponse response = mock(OrderResponse.class);
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        assertSame(response, orderService.cancelCustomerOrder(orderId, auth));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(OrderItemStatus.CANCELLED, line1.getStatus());
        assertEquals(OrderItemStatus.CANCELLED, line2.getStatus());
        verify(orderRepository).save(order);
        verify(orderItemRepository).saveAll(List.of(line1, line2));
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void rejectsCancelWhenOrderNotOwnedOrNotCancellable() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).customerId(user).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.cancelCustomerOrder(orderId, auth));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void searchReturnsOrdersMatchingKeyword() {
        UUID user = UUID.randomUUID(), orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).customerId(user).status(OrderStatus.PENDING_CONFIRMATION).build();
        OrderResponse response = mock(OrderResponse.class);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.searchByCustomerAndKeyword(eq(user), eq("test-keyword"), any(Pageable.class)))
                .thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        PageResponse<OrderResponse> result = orderService.searchCustomerOrders(auth, "test-keyword", 0, 20);
        assertEquals(1, result.getContent().size());
        assertSame(response, result.getContent().get(0));
    }

    @Test
    void searchRejectsBlankKeyword() {
        UUID user = UUID.randomUUID();
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.searchCustomerOrders(auth, "  ", 0, 20));
        verify(orderRepository, never()).searchByCustomerAndKeyword(any(), any(), any());
    }

    @Test
    void getOrdersRejectsInvalidPagination() {
        UUID user = UUID.randomUUID();
        var auth = customerAuth(user);

        assertThrows(RuntimeException.class, () -> orderService.getCustomerOrders(auth, null, -1, 20));
        assertThrows(RuntimeException.class, () -> orderService.getCustomerOrders(auth, null, 0, 0));
        assertThrows(RuntimeException.class, () -> orderService.getCustomerOrders(auth, null, 0, 101));
        verify(orderRepository, never()).findByCustomerId(any(), any());
    }
}
