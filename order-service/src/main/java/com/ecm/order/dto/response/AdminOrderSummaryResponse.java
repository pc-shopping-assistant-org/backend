package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the order list of the shop. */
public record AdminOrderSummaryResponse(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        String recipientName,
        String recipientPhone,
        OrderStatus status,
        Long totalAmount,
        int itemCount,
        String firstProductName,
        Instant createdAt
) {
}
