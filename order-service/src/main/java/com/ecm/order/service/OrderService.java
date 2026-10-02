package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.entity.*;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.messaging.rabbitmq.RabbitTopology;
import com.ecm.order.messaging.rabbitmq.command.ReserveStockCommand;
import com.ecm.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String VARIANT_STATUS_ACTIVE = "ACTIVE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CatalogServiceClient catalogServiceClient;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Authentication authentication, String sessionToken) {
        UUID accountId = CurrentUser.accountId(authentication);

        // 1. A duplicate checkout submission with the same key returns the original order.
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            if (accountId == null || !Objects.equals(existing.get().getCustomerId(), accountId)) {
                throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
            }
            return toResponse(existing.get());
        }

        // 2. Load and authorize the cart using the verified owner, never a client-selected cart id.
        Cart cart = findOwnedCart(accountId, normalizeSession(sessionToken));
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new BusinessException(OrderErrorCode.CART_NOT_ACTIVE);
        }
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_EMPTY);
        }

        // 3. Re-price every line against the catalog's current price/stock — never trust a
        // client-supplied price — and reject the whole checkout if anything is unavailable.
        List<OrderItem> items = priceCartItems(cartItems);
        long subtotal;
        try {
            subtotal = items.stream().mapToLong(item -> Math.multiplyExact(item.getUnitPrice(), (long) item.getQuantity()))
                    .reduce(0L, Math::addExact);
        } catch (ArithmeticException ex) {
            throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
        }

        // 4. Snapshot the shipping fee at order time.
        ShippingMethod shippingMethod = shippingMethodRepository.findById(request.shippingMethodId())
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingMethod", request.shippingMethodId()));

        // 5. Persist order + items, convert the cart, and enqueue stock reservation commands
        // for the saga — all in the same transaction as the order/cart state change.
        Order order = persistOrder(request, cart, items, shippingMethod, subtotal);

        // 6. Map to response.
        return toResponse(order);
    }

    private Cart findOwnedCart(UUID accountId, String sessionToken) {
        if ((accountId == null) == (sessionToken == null)) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        return (accountId != null
                ? cartRepository.lockActiveByCustomerId(accountId, CartStatus.ACTIVE)
                : cartRepository.lockActiveBySessionToken(sessionToken, CartStatus.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("Cart", accountId != null ? accountId : sessionToken));
    }

    private String normalizeSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return null;
        }
        String normalized = sessionToken.trim();
        if (normalized.length() > 255) {
            throw new BusinessException(OrderErrorCode.CART_SESSION_REQUIRED);
        }
        return normalized;
    }

    private List<OrderItem> priceCartItems(List<CartItem> cartItems) {
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            ProductVariantResponse variant = catalogServiceClient.getVariant(cartItem.getVariantId()).getData();
            if (variant == null || !VARIANT_STATUS_ACTIVE.equals(variant.status())) {
                throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE);
            }
            if (variant.quantity() == null || variant.quantity() < cartItem.getQuantity()) {
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK);
            }
            items.add(OrderItem.builder()
                    .productVariantId(cartItem.getVariantId())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(variant.listPrice())
                    .itemDiscount(0L)
                    .status(OrderItemStatus.ACTIVE)
                    .build());
        }
        return items;
    }

    private Order persistOrder(
            CreateOrderRequest request, Cart cart, List<OrderItem> items, ShippingMethod shippingMethod, long subtotal) {
        Order order = Order.builder()
                .customerId(cart.getCustomerId())
                .shippingMethodId(shippingMethod.getId())
                .paymentMethodId(request.paymentMethodId())
                .idempotencyKey(request.idempotencyKey())
                .subtotalAmount(subtotal)
                .discountAmount(0L)
                .shippingFee(shippingMethod.getFee())
                .totalAmount(addExact(subtotal, shippingMethod.getFee()))
                .orderTime(Instant.now())
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .deliveryAddress(request.deliveryAddress())
                .note(request.note())
                // Online vs COD payment split (doc: online -> PENDING_PAYMENT, COD ->
                // PENDING_CONFIRMATION) needs the chosen payment method's type, which isn't
                // available cross-service yet — every order starts PENDING_CONFIRMATION until then.
                .status(OrderStatus.PENDING_CONFIRMATION)
                .build();
        // A concurrent retry with the same idempotency key can still race past the check in
        // createOrder() — the unique index is the real guard. Postgres aborts the whole
        // transaction after a constraint violation, so this can't recover by querying again
        // in the same transaction; translate to a clean error and let the caller retry, which
        // will then hit the fast path in createOrder() step 1.
        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "This order was already submitted concurrently; please retry", ex);
        }

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        items = orderItemRepository.saveAll(items);

        cart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(cart);

        enqueueReservations(order, items);
        return order;
    }

    private long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
        }
    }

    @SneakyThrows
    private void enqueueReservations(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            ReserveStockCommand command = new ReserveStockCommand(
                    UUID.randomUUID(), order.getId(), item.getProductVariantId(), item.getQuantity());
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE_ORDER)
                    .aggregateId(order.getId())
                    .eventType("ReserveStockCommand")
                    .channel(OutboxChannel.RABBITMQ)
                    .destination(RabbitTopology.ROUTING_KEY_RESERVE)
                    .payload(objectMapper.writeValueAsString(command))
                    .status(OutboxStatus.PENDING)
                    .build());
        }
    }

    private OrderResponse toResponse(Order order) {
        return orderMapper.toResponse(order, orderItemRepository.findByOrderId(order.getId()));
    }
}
