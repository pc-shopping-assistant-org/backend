package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as it was placed, with the payment attempts the Payment Service holds for it. */
public record OrderDetailResponse(
        UUID id,
        String invoiceNumber,
        OrderStatus status,
        String cancellationReason,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        String note,
        UUID shippingMethodId,
        UUID paymentMethodId,
        List<OrderItemResponse> items,
        Long subtotalAmount,
        Long discountAmount,
        Long shippingFee,
        Long totalAmount,
        List<PaymentResponse> payments,
        Instant createdAt,
        Instant deliveredAt
) {
}
