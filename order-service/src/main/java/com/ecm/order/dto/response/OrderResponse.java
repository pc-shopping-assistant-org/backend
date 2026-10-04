package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        OrderStatus status,
        String invoiceNumber,
        Long subtotalAmount,
        Long discountAmount,
        Long shippingFee,
        Long totalAmount,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        Instant orderTime,
        List<OrderItemResponse> items) {
}
