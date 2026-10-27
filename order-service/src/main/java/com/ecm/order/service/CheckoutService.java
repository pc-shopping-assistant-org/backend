package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.order.dto.request.CreateOrderRequest;
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
import com.ecm.order.helper.PaymentLookup;
import com.ecm.order.helper.RecipientResolver;
import com.ecm.order.helper.CatalogVariantLookup;
import com.ecm.order.helper.InvoiceNumberGenerator;
import com.ecm.order.helper.OrderOutbox;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.CartItemRepository;
import com.ecm.order.repository.CartRepository;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.ShippingMethodRepository;
import com.ecm.order.util.OrderLines;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Places an order from the cart of a customer: UC-ORD-001. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final String COD_PAYMENT_METHOD_CODE = "COD";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final CatalogVariantLookup variantLookup;
    private final OrderDiscountService discountService;
    private final PaymentLookup paymentLookup;
    private final RecipientResolver recipientResolver;
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
        List<OrderItem> items = OrderLines.snapshot(cartItems, variants);

        // 3. Delivery details, shipping method and payment method
        RecipientResolver.Recipient recipient = recipientResolver.resolve(request, bearerToken);
        ShippingMethod shippingMethod = shippingMethodRepository.findById(request.shippingMethodId())
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_SHIPPING_METHOD));
        PaymentMethodResponse paymentMethod = paymentLookup.method(request.paymentMethodId(), bearerToken);

        // 4. Discounts: at most one per line and one voucher for the order
        long grossSubtotal = items.stream().mapToLong(item -> item.getUnitPrice() * item.getQuantity()).sum();
        DiscountApplyResponse discounts = discountService.apply(request.discountCode(), OrderLines.forDiscounts(items, variants), grossSubtotal, bearerToken);
        OrderLines.applyDiscounts(items, discounts);

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

    private Order saveOrder(CreateOrderRequest request, UUID customerId, RecipientResolver.Recipient recipient, ShippingMethod shippingMethod,
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
}
