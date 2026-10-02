package com.ecm.order.service;

import com.ecm.common.response.ApiResponse;
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
    @Mock OrderMapper orderMapper;
    @Mock ObjectMapper objectMapper;
    @InjectMocks OrderService orderService;

    @Test
    void checkoutRepricesCartConvertsCartAndWritesOutbox() throws Exception {
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
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success("ok", variant));
        when(shippingMethodRepository.findById(shippingId)).thenReturn(Optional.of(shipping));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderItemRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of());
        when(orderMapper.toResponse(eq(order), anyList())).thenReturn(response);
        var auth = customerAuth(user);

        assertSame(response, orderService.createOrder(new CreateOrderRequest("key-1", shippingId, UUID.randomUUID(),
                "Customer", "09123456789", "Address", null), auth, null));
        assertEquals(CartStatus.CONVERTED, cart.getStatus());
        verify(orderRepository).save(argThat(saved -> saved.getSubtotalAmount() == 500L
                && saved.getShippingFee() == 500L && saved.getTotalAmount() == 1000L));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
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
        when(catalogServiceClient.getVariant(variantId)).thenReturn(ApiResponse.success("ok",
                new ProductVariantResponse(variantId, UUID.randomUUID(), 1L, 5, "sku", "model", "ACTIVE", null, null, List.of())));
        when(shippingMethodRepository.findById(shippingId)).thenReturn(Optional.empty());
        var auth = customerAuth(user);
        assertThrows(RuntimeException.class, () -> orderService.createOrder(new CreateOrderRequest("key-2", shippingId,
                UUID.randomUUID(), "Customer", "09123456789", "Address", null), auth, null));
        verify(orderRepository, never()).save(any(Order.class));
    }
}
