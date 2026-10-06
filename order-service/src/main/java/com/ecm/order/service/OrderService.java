package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.common.security.CurrentUser;
import com.ecm.order.client.CatalogServiceClient;
import com.ecm.order.client.PromotionServiceClient;
import com.ecm.order.dto.request.ApplyDiscountRequest;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.request.AdminOrderSearchRequest;
import com.ecm.order.dto.response.OrderResponse;
import com.ecm.order.dto.response.DiscountApplyResponse;
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
    private static final String NO_UPPER_BOUND = "9999-12-31T00:00:00Z";
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
    private final PromotionServiceClient promotionServiceClient;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAdminOrders(AdminOrderSearchRequest filter, int page, int size) {
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_PAGE_SIZE
                || filter.createdFrom() != null && filter.createdTo() != null && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderTime"));
        Instant createdFrom = filter.createdFrom() == null ? Instant.EPOCH : filter.createdFrom();
        Instant createdTo = filter.createdTo() == null ? Instant.parse(NO_UPPER_BOUND) : filter.createdTo();
        Page<Order> orders = orderRepository.searchAdminOrders(filter.status(), filter.customerId(),
                createdFrom, createdTo, keyword, pageable);
        return PageResponse.of(orders.map(this::toResponse));
    }

    @Transactional
    public OrderResponse updateAdminOrderStatus(UUID orderId, OrderStatus status, UUID employeeId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!isValidAdminTransition(order.getStatus(), status)) throw new BusinessException(OrderErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        order.setStatus(status);
        order.setUpdatedBy(employeeId);
        if (status == OrderStatus.COMPLETED) order.setDeliveredAt(Instant.now());
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        items.forEach(item -> item.setStatus(status == OrderStatus.CANCELLED ? OrderItemStatus.CANCELLED : OrderItemStatus.ACTIVE));
        orderItemRepository.saveAll(items);
        Order saved = orderRepository.save(order);
        if (status == OrderStatus.CANCELLED) enqueueStockReleases(saved, items);
        return toResponse(saved);
    }

    private boolean isValidAdminTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING_CONFIRMATION -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SHIPPING || next == OrderStatus.CANCELLED;
            case SHIPPING -> next == OrderStatus.COMPLETED;
            default -> false;
        };
    }

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
        // 1. Verify customer identity and lock the order to prevent concurrent state changes.
        UUID accountId = requireCustomerAccountId(authentication);
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // 2. Verify ownership and cancellable status.
        if (!order.getCustomerId().equals(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        // 3. Cancel the order and its items in the current transaction.
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        order.setStatus(OrderStatus.CANCELLED);
        items.forEach(item -> item.setStatus(OrderItemStatus.CANCELLED));
        orderItemRepository.saveAll(items);
        orderRepository.save(order);

        // 4. Release reserved stock through the transactional outbox.
        enqueueStockReleases(order, items);

        // 5. Map the cancelled order to the API response.
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> searchCustomerOrders(Authentication authentication, String keyword, int page, int size) {
        // 1. Verify customer identity.
        UUID accountId = requireCustomerAccountId(authentication);

        // 2. Validate pagination and search only this customer's orders.
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderTime"));
        Page<Order> orders = orderRepository.searchByCustomerAndKeyword(accountId, keyword.trim(), pageable);

        // 3. Convert the result page to its API representation.
        return PageResponse.of(orders.map(this::toResponse));
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
    public OrderResponse createOrder(CreateOrderRequest request, Authentication authentication) {
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
        Cart cart = cartRepository.lockByCustomerId(accountId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_EMPTY));
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

        ShippingMethod shippingMethod = shippingMethodRepository.findById(request.shippingMethodId())
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingMethod", request.shippingMethodId()));
        DiscountApplyResponse appliedDiscounts = evaluateDiscounts(request, authentication, items, subtotal);
        items = applyItemDiscounts(items, appliedDiscounts);
        // 9. Persist order state and stock reservation commands in one transaction.
        Order order = persistOrder(request, cart, items, shippingMethod, subtotal, appliedDiscounts);

        // 10. Map the saved order and its items to the API response.
        return toResponse(order);
    }

    private DiscountApplyResponse evaluateDiscounts(CreateOrderRequest request, Authentication authentication,
                                                     List<OrderItem> items, long subtotal) {
        try {
            List<ApplyDiscountRequest.DiscountCartItemRequest> lines = new ArrayList<>();
            for (OrderItem item : items) {
                ProductVariantResponse variant = catalogServiceClient.getVariant(item.getProductVariantId()).getData();
                var product = catalogServiceClient.getProduct(variant.productId()).getData();
                if (product == null || product.categoryId() == null) {
                    throw new com.ecm.common.exception.ExternalServiceException("catalog-service", "Missing product category");
                }
                lines.add(new ApplyDiscountRequest.DiscountCartItemRequest(item.getProductVariantId(),
                        item.getQuantity(), item.getUnitPrice(), product.categoryId()));
            }
            String bearer = "Bearer " + ((org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken)
                    authentication).getToken().getTokenValue();
            var response = promotionServiceClient.apply(new ApplyDiscountRequest(request.discountCode(), subtotal,
                    lines, request.idempotencyKey()), bearer);
            if (response == null || response.getData() == null) {
                throw new com.ecm.common.exception.ExternalServiceException("promotion-service", "Invalid discount response");
            }
            DiscountApplyResponse result = response.getData();
            if (result.discountAmount() == null || result.discountAmount() < 0 || result.discountAmount() > subtotal
                    || result.orderDiscountAmount() == null || result.orderDiscountAmount() < 0 || result.itemDiscounts() == null) {
                throw new com.ecm.common.exception.ExternalServiceException("promotion-service", "Invalid discount amounts");
            }
            long total = result.orderDiscountAmount();
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (var line : result.itemDiscounts()) {
                if (line.discountAmount() == null || line.discountAmount() < 0 || line.discountId() == null
                        || !seen.add(line.productVariantId())) {
                    throw new com.ecm.common.exception.ExternalServiceException("promotion-service", "Invalid item discounts");
                }
                total = addExact(total, line.discountAmount());
            }
            if (total != result.discountAmount()) {
                throw new com.ecm.common.exception.ExternalServiceException("promotion-service", "Inconsistent discount total");
            }
            return result;
        } catch (feign.FeignException ex) {
            throw new com.ecm.common.exception.ExternalServiceException("promotion-service", ex);
        }
    }
    private List<OrderItem> applyItemDiscounts(List<OrderItem> items, DiscountApplyResponse discounts) {
        if (discounts.itemDiscounts() == null) {
            return items;
        }
        for (var itemDiscount : discounts.itemDiscounts()) {
            OrderItem item = items.stream().filter(candidate -> candidate.getProductVariantId().equals(itemDiscount.productVariantId()))
                    .findFirst().orElseThrow(() -> new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE));
            long lineAmount = multiplyExact(item.getUnitPrice(), item.getQuantity());
            if (itemDiscount.discountAmount() < 0 || itemDiscount.discountAmount() > lineAmount) {
                throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
            }
            item.setItemDiscountId(itemDiscount.discountId());
            item.setItemDiscount(itemDiscount.discountAmount());
        }
        return items;
    }

    private long multiplyExact(long price, int quantity) {
        try {
            return Math.multiplyExact(price, quantity);
        } catch (ArithmeticException ex) {
            throw new BusinessException(OrderErrorCode.CART_QUANTITY_TOO_LARGE);
        }
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
                    .unitPrice(variant.price())
                    .itemDiscount(0L)
                    .status(OrderItemStatus.ACTIVE)
                    .build());
        }
        return items;
    }

    private Order persistOrder(
            CreateOrderRequest request, Cart cart, List<OrderItem> items, ShippingMethod shippingMethod, long subtotal,
            DiscountApplyResponse appliedDiscounts) {
        Order order = Order.builder()
                .customerId(cart.getCustomerId())
                .shippingMethodId(shippingMethod.getId())
                .paymentMethodId(request.paymentMethodId())
                .idempotencyKey(request.idempotencyKey())
                .subtotalAmount(subtotal)
                .orderDiscountId(appliedDiscounts.orderDiscountId())
                .discountAmount(appliedDiscounts.discountAmount())
                .shippingFee(shippingMethod.getFee())
                .totalAmount(addExact(subtotal - appliedDiscounts.discountAmount(), shippingMethod.getFee()))
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

        // 2. Save the order lines and empty the cart.
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        items = orderItemRepository.saveAll(items);

        cartItemRepository.deleteByCartId(cart.getId());

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
