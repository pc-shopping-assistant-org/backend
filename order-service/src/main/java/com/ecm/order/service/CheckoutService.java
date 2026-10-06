package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.IdentityServiceClient;
import com.ecm.order.client.PaymentServiceClient;
import com.ecm.order.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
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
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Places an order from the cart of a customer: UC-ORD-001. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String COD_PAYMENT_METHOD_CODE = "COD";
    private static final String IDENTITY_SERVICE = "identity-service";
    private static final String PAYMENT_SERVICE = "payment-service";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final CatalogVariantLookup variantLookup;
    private final OrderDiscountService discountService;
    private final PaymentServiceClient paymentServiceClient;
    private final IdentityServiceClient identityServiceClient;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final OrderStatusService orderStatusService;
    private final OrderOutbox orderOutbox;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderDetailResponse placeOrder(CreateOrderRequest request, UUID customerId, String bearerToken) {
        // 1. A retry with the same idempotency key returns the order it created the first time
        Order existing = orderRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getCustomerId().equals(customerId)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "Idempotency key already used");
            }
            return orderMapper.toDetail(existing, orderItemRepository.findByOrderId(existing.getId()), List.of(), null);
        }

        // 2. The cart of the customer must hold something that can be bought now, in the stock available
        Cart cart = cartRepository.lockByCustomerId(customerId).orElseThrow(() -> new BusinessException(OrderErrorCode.CART_EMPTY));
        List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());
        if (cartItems.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_EMPTY);
        }
        Map<UUID, CartVariantDetailsResponse> variants = variantLookup.details(cartItems.stream().map(CartItem::getVariantId).toList()).stream()
                .collect(Collectors.toMap(CartVariantDetailsResponse::id, Function.identity()));
        List<OrderItem> items = snapshotLines(cartItems, variants);

        // 3. Delivery details, shipping method and payment method
        Recipient recipient = resolveRecipient(request, bearerToken);
        ShippingMethod shippingMethod = shippingMethodRepository.findById(request.shippingMethodId())
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_SHIPPING_METHOD));
        PaymentMethodResponse paymentMethod = resolvePaymentMethod(request.paymentMethodId(), bearerToken);

        // 4. Discounts: at most one per line and one voucher for the order
        long grossSubtotal = items.stream().mapToLong(item -> item.getUnitPrice() * item.getQuantity()).sum();
        DiscountApplyResponse discounts = discountService.apply(request.discountCode(), discountLines(items, variants), grossSubtotal, bearerToken);
        applyItemDiscounts(items, discounts);

        // 5. Money: the subtotal is after the line discounts, the voucher comes off that, then the shipping fee is added
        long subtotal = grossSubtotal - discounts.itemDiscounts().stream().mapToLong(ItemDiscountApplyResponse::discountAmount).sum();
        long orderDiscount = discounts.orderDiscountAmount();
        long total = subtotal - orderDiscount + shippingMethod.getFee();

        // 6. Save the order, its lines and its first status row, and empty the cart
        Order order = saveOrder(request, customerId, recipient, shippingMethod, paymentMethod, discounts, subtotal, orderDiscount, total);
        items.forEach(item -> item.setOrderId(order.getId()));
        List<OrderItem> savedItems = orderItemRepository.saveAll(items);
        orderStatusService.recordInitial(order);
        cartItemRepository.deleteByCartId(cart.getId());

        // 7. Reserve the stock through the outbox, in the same transaction as the order
        orderOutbox.reserveStock(order, savedItems);
        return orderMapper.toDetail(order, savedItems, List.of(), null);
    }

    /** Copies each cart line with the name, SKU, label and price it has now; rejects lines off sale or short of stock. */
    private List<OrderItem> snapshotLines(List<CartItem> cartItems, Map<UUID, CartVariantDetailsResponse> variants) {
        List<String> unavailable = new ArrayList<>();
        List<String> lowStock = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            CartVariantDetailsResponse variant = variants.get(cartItem.getVariantId());
            if (variant == null || !variant.sellable()) {
                unavailable.add(variant == null ? cartItem.getVariantId().toString() : variant.sku());
            } else if (variant.quantity() == null || variant.quantity() < cartItem.getQuantity()) {
                lowStock.add(variant.sku() + " (requested " + cartItem.getQuantity() + ", available " + variant.quantity() + ")");
            } else {
                items.add(OrderItem.builder().productVariantId(variant.id()).productName(variant.productName()).sku(variant.sku())
                        .variantLabel(variant.variantLabel()).quantity(cartItem.getQuantity()).unitPrice(variant.price()).discountAmount(0L).build());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new BusinessException(OrderErrorCode.VARIANT_NOT_AVAILABLE, "No longer available: " + String.join(", ", unavailable));
        }
        if (!lowStock.isEmpty()) {
            throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK, "Not enough stock: " + String.join(", ", lowStock));
        }
        return items;
    }

    private List<DiscountCartItemRequest> discountLines(List<OrderItem> items, Map<UUID, CartVariantDetailsResponse> variants) {
        return items.stream().map(item -> new DiscountCartItemRequest(item.getProductVariantId(), item.getQuantity(),
                item.getUnitPrice(), variants.get(item.getProductVariantId()).categoryId())).toList();
    }

    private void applyItemDiscounts(List<OrderItem> items, DiscountApplyResponse discounts) {
        Map<UUID, OrderItem> byVariant = items.stream().collect(Collectors.toMap(OrderItem::getProductVariantId, Function.identity()));
        discounts.itemDiscounts().forEach(itemDiscount -> {
            OrderItem item = byVariant.get(itemDiscount.productVariantId());
            item.setDiscountId(itemDiscount.discountId());
            item.setDiscountAmount(itemDiscount.discountAmount());
        });
    }

    private Order saveOrder(CreateOrderRequest request, UUID customerId, Recipient recipient, ShippingMethod shippingMethod,
                            PaymentMethodResponse paymentMethod, DiscountApplyResponse discounts, long subtotal, long orderDiscount, long total) {
        // An online order waits for its payment; a cash-on-delivery order waits for the shop to confirm it
        OrderStatus initialStatus = COD_PAYMENT_METHOD_CODE.equals(paymentMethod.code())
                ? OrderStatus.PENDING_CONFIRMATION : OrderStatus.PENDING_PAYMENT;
        try {
            return orderRepository.saveAndFlush(Order.builder()
                    .customerId(customerId).shippingMethodId(shippingMethod.getId()).paymentMethodId(paymentMethod.id())
                    .idempotencyKey(request.idempotencyKey()).invoiceNumber(invoiceNumberGenerator.next())
                    .orderDiscountId(discounts.orderDiscountId()).subtotalAmount(subtotal).discountAmount(orderDiscount)
                    .shippingFee(shippingMethod.getFee()).totalAmount(total).note(request.note())
                    .recipientName(recipient.name()).recipientPhone(recipient.phone()).deliveryAddress(recipient.address())
                    .status(initialStatus).build());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "This order was already submitted concurrently; please retry", ex);
        }
    }

    /** A saved address of the customer, or the recipient typed in; never a mix, and never an address of someone else. */
    private Recipient resolveRecipient(CreateOrderRequest request, String bearerToken) {
        if (request.customerAddressId() != null) {
            if (request.recipientName() != null || request.recipientPhone() != null || request.deliveryAddress() != null) {
                throw new BusinessException(OrderErrorCode.INVALID_RECIPIENT);
            }
            AddressResponse address = fetchAddresses(bearerToken).stream()
                    .filter(saved -> saved.id().equals(request.customerAddressId())).findFirst()
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_RECIPIENT));
            return new Recipient(address.recipientName(), address.phone(), address.addressLine());
        }
        if (isBlank(request.recipientName()) || isBlank(request.recipientPhone()) || isBlank(request.deliveryAddress())) {
            throw new BusinessException(OrderErrorCode.INVALID_RECIPIENT);
        }
        return new Recipient(request.recipientName().trim(), request.recipientPhone().trim(), request.deliveryAddress().trim());
    }

    private List<AddressResponse> fetchAddresses(String bearerToken) {
        try {
            return Objects.requireNonNull(identityServiceClient.getMyAddresses(bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(IDENTITY_SERVICE, ex);
        }
    }

    private PaymentMethodResponse resolvePaymentMethod(UUID paymentMethodId, String bearerToken) {
        List<PaymentMethodResponse> methods;
        try {
            methods = Objects.requireNonNull(paymentServiceClient.getPaymentMethods(bearerToken).getData());
        } catch (FeignException ex) {
            throw new ExternalServiceException(PAYMENT_SERVICE, ex);
        }
        return methods.stream().filter(method -> method.id().equals(paymentMethodId)).findFirst()
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_PAYMENT_METHOD));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Recipient(String name, String phone, String address) {
    }
}
