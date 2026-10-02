package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
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
import com.ecm.order.messaging.rabbitmq.command.ReleaseStockCommand;
import com.ecm.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CatalogServiceClient catalogServiceClient;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getCustomerOrders(Authentication authentication, OrderStatus status, int page, int size) {
        // 1. Verify customer identity before accessing any order data.
        UUID accountId = requireCustomerAccountId(authentication);

        // 2. Validate pagination and query only this customer's orders.
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderTime"));
        Page<Order> orders = status == null
                ? orderRepository.findByCustomerId(accountId, pageable)
                : orderRepository.findByCustomerIdAndStatus(accountId, status, pageable);

        // 3. Convert the result page to its API representation.
        return PageResponse.of(orders.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrder(UUID orderId, Authentication authentication) {
        // 1. Verify customer identity and retrieve only an order owned by that customer.
        UUID accountId = requireCustomerAccountId(authentication);
        Order order = orderRepository.findByIdAndCustomerId(orderId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Map the authorized order and its items to the API response.
        return toResponse(order);
    }

    @Transactional
    @SneakyThrows
    public OrderResponse cancelCustomerOrder(UUID orderId, Authentication authentication) {
        // 1. Verify customer identity and find an owned order that is still cancellable.
        UUID accountId = requireCustomerAccountId(authentication);
        Order order = orderRepository.findByIdAndCustomerIdAndStatus(orderId, accountId, OrderStatus.PENDING_CONFIRMATION)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Cancel the order and its items in the current transaction.
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        order.setStatus(OrderStatus.CANCELLED);
        items.forEach(item -> item.setStatus(OrderItemStatus.CANCELLED));
        orderItemRepository.saveAll(items);
        orderRepository.save(order);

        // 3. Release reserved stock through the transactional outbox.
        enqueueStockReleases(order, items);

        // 4. Map the cancelled order to the API response.
        return toResponse(order);
    }

    private UUID requireCustomerAccountId(Authentication authentication) {
        UUID accountId = CurrentUser.accountId(authentication);
        if (accountId == null) {
            throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
        }
        return accountId;
    }

    @SneakyThrows
    private void enqueueStockReleases(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            ReleaseStockCommand command = new ReleaseStockCommand(
                    UUID.randomUUID(), order.getId(), item.getProductVariantId(), item.getQuantity());
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(AGGREGATE_TYPE_ORDER)
                    .aggregateId(order.getId())
                    .eventType("ReleaseStockCommand")
                    .channel(OutboxChannel.RABBITMQ)
                    .destination(RabbitTopology.ROUTING_KEY_RELEASE)
                    .payload(objectMapper.writeValueAsString(command))
                    .status(OutboxStatus.PENDING)
                    .build());
        }
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, Authentication authentication, String sessionToken) {
        // 1. Verify customer identity before looking up or creating orders.
        UUID accountId = requireCustomerAccountId(authentication);

        // 2. Return the original order for an idempotent retry by its owner.
        Optional<Order> existing = orderRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getCustomerId(), accountId)) {
                throw new BusinessException(OrderErrorCode.CART_OWNER_REQUIRED);
            }
            return toResponse(existing.get());
        }

        // 3. Load and authorize the customer's active cart, never a client-selected cart ID.
        Cart cart = findOwnedCart(accountId, normalizeSession(sessionToken));
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new BusinessException(OrderErrorCode.CART_NOT_ACTIVE);
        }
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_EMPTY);
        }

        // 4. Re-price every line against current catalog data; never trust a client-supplied price.
        List<OrderItem> items = priceCartItems(cartItems);
        // 5. Calculate the subtotal and reject arithmetic overflow.
        long subtotal;
        try {
            subtotal = items.stream().mapToLong(item -> Math.multiplyExact(item.getUnitPrice(), (long) item.getQuantity()))
                    .reduce(0L, Math::addExact);
        } catch (ArithmeticException ex) {
            throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
        }

        // 6. Snapshot the fee from an active shipping method.
        ShippingMethod shippingMethod = shippingMethodRepository.findById(request.shippingMethodId())
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingMethod", request.shippingMethodId()));

        // 7. Persist order state and stock reservation commands in one transaction.
        Order order = persistOrder(request, cart, items, shippingMethod, subtotal);

        // 8. Map the saved order and its items to the API response.
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
        // 1. Re-price each cart line and reject unavailable variants or insufficient stock.
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
                // Payment-method type is not yet available across services, so checkout currently starts every order in PENDING_CONFIRMATION.
                .status(OrderStatus.PENDING_CONFIRMATION)
                .build();
        // 1. Save the order; the unique index resolves concurrent idempotency-key retries.
        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "This order was already submitted concurrently; please retry", ex);
        }

        // 2. Save the order lines and convert the active cart.
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        items = orderItemRepository.saveAll(items);

        cart.setStatus(CartStatus.CONVERTED);
        cartRepository.save(cart);

        // 3. Queue reservation commands so they commit atomically with the order and cart.
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
        // 1. Queue one stock reservation command per order line through the outbox.
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
