package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.dto.response.ItemDiscountApplyResponse;
import com.ecm.order.dto.response.OrderDetailResponse;
import com.ecm.order.dto.response.PaymentMethodResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderItem;
import com.ecm.order.entity.ShippingMethod;
import com.ecm.order.entity.ShippingMethodStatus;
import com.ecm.order.exception.OrderErrorCode;
import com.ecm.order.helper.CheckoutCart;
import com.ecm.order.helper.OrderOutbox;
import com.ecm.order.helper.OrderWriter;
import com.ecm.order.helper.PaymentLookup;
import com.ecm.order.helper.RecipientResolver;
import com.ecm.order.mapper.OrderMapper;
import com.ecm.order.repository.OrderItemRepository;
import com.ecm.order.repository.OrderRepository;
import com.ecm.order.repository.ShippingMethodRepository;
import com.ecm.order.util.OrderLines;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Places an order from the cart of a customer: UC-ORD-001. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShippingMethodRepository shippingMethodRepository;
    private final CheckoutCart checkoutCart;
    private final OrderDiscountService discountService;
    private final PaymentLookup paymentLookup;
    private final RecipientResolver recipientResolver;
    private final OrderWriter orderWriter;
    private final OrderStatusService orderStatusService;
    private final OrderOutbox orderOutbox;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderDetailResponse placeOrder(CreateOrderRequest request, UUID customerId, String bearerToken) {
        // 1. A retry with the same idempotency key returns the order it created the first time
        Order existing = orderRepository.findByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            return existingOrder(existing, customerId);
        }

        // 2. The cart of the customer must hold something that can be bought now, in the stock available
        CheckoutCart.Contents cart = checkoutCart.lockAndSnapshot(customerId);
        List<OrderItem> items = cart.items();

        // 3. Delivery details, shipping method and payment method
        RecipientResolver.Recipient recipient = recipientResolver.resolve(request, bearerToken);
        ShippingMethod shippingMethod = activeShippingMethod(request.shippingMethodId());
        PaymentMethodResponse paymentMethod = paymentLookup.method(request.paymentMethodId(), bearerToken);

        // 4. Discounts: at most one per line and one voucher for the order
        long grossSubtotal = items.stream().mapToLong(item -> item.getUnitPrice() * item.getQuantity()).sum();
        DiscountApplyResponse discounts = discountService.apply(request.discountCode(), OrderLines.forDiscounts(items, cart.variants()), grossSubtotal, bearerToken);
        OrderLines.applyDiscounts(items, discounts);

        // 5. Money
        long itemDiscounts = discounts.itemDiscounts().stream().mapToLong(ItemDiscountApplyResponse::discountAmount).sum();
        OrderWriter.Amounts amounts = OrderWriter.Amounts.of(grossSubtotal - itemDiscounts, discounts.orderDiscountAmount(), shippingMethod.getFee());

        // 6. Save the order, its lines and its first status row, and empty the cart
        Order order = orderWriter.save(request, customerId, recipient, shippingMethod, paymentMethod, discounts, amounts);
        items.forEach(item -> item.setOrderId(order.getId()));
        List<OrderItem> savedItems = orderItemRepository.saveAll(items);
        orderStatusService.recordInitial(order);
        checkoutCart.empty(cart.cart());

        // 7. Reserve the stock through the outbox, in the same transaction as the order
        orderOutbox.reserveStock(order, savedItems);
        return orderMapper.toDetail(order, savedItems, List.of(), null);
    }

    /** The order a retry finds; a key another customer used is rejected. */
    private OrderDetailResponse existingOrder(Order existing, UUID customerId) {
        if (!existing.getCustomerId().equals(customerId)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "Idempotency key already used");
        }
        return orderMapper.toDetail(existing, orderItemRepository.findByOrderId(existing.getId()), List.of(), null);
    }

    private ShippingMethod activeShippingMethod(UUID shippingMethodId) {
        return shippingMethodRepository.findById(shippingMethodId)
                .filter(method -> method.getStatus() == ShippingMethodStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_SHIPPING_METHOD));
    }
}
