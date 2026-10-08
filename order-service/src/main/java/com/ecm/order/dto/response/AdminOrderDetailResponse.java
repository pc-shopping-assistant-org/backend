package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as it was placed, read from its own snapshot, with its payment attempts and every status change. */
public record AdminOrderDetailResponse(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        OrderStatus status,
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
        List<OrderStatusHistoryResponse> statusHistory,
        Instant createdAt,
        Instant deliveredAt
) {
}
