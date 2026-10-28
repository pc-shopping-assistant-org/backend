package com.ecm.order.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.order.dto.request.CreateOrderRequest;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.dto.response.PaymentMethodResponse;
import com.ecm.order.entity.Order;
import com.ecm.order.entity.OrderStatus;
import com.ecm.order.entity.ShippingMethod;
import com.ecm.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Saves the order row of a checkout. */
@Component
@RequiredArgsConstructor
public class OrderWriter {

    private static final String COD_PAYMENT_METHOD_CODE = "COD";

    private final OrderRepository orderRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;

    /** The amounts of an order: the subtotal is after the line discounts, the voucher comes off that, then the shipping fee is added. */
    public record Amounts(long subtotal, long orderDiscount, long total) {

        public static Amounts of(long subtotal, long orderDiscount, long shippingFee) {
            return new Amounts(subtotal, orderDiscount, subtotal - orderDiscount + shippingFee);
        }
    }

    /** An online order waits for its payment; a cash-on-delivery order waits for the shop to confirm it. A concurrent submit of the same key is a conflict. */
    public Order save(CreateOrderRequest request, UUID customerId, RecipientResolver.Recipient recipient, ShippingMethod shippingMethod,
                      PaymentMethodResponse paymentMethod, DiscountApplyResponse discounts, Amounts amounts) {
        OrderStatus initialStatus = COD_PAYMENT_METHOD_CODE.equals(paymentMethod.code())
                ? OrderStatus.PENDING_CONFIRMATION : OrderStatus.PENDING_PAYMENT;
        try {
            return orderRepository.saveAndFlush(Order.builder()
                    .customerId(customerId).shippingMethodId(shippingMethod.getId()).paymentMethodId(paymentMethod.id())
                    .idempotencyKey(request.idempotencyKey()).invoiceNumber(invoiceNumberGenerator.next())
                    .orderDiscountId(discounts.orderDiscountId()).subtotalAmount(amounts.subtotal()).discountAmount(amounts.orderDiscount())
                    .shippingFee(shippingMethod.getFee()).totalAmount(amounts.total()).note(request.note())
                    .recipientName(recipient.name()).recipientPhone(recipient.phone()).deliveryAddress(recipient.address())
                    .status(initialStatus).build());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "This order was already submitted concurrently; please retry", ex);
        }
    }
}
