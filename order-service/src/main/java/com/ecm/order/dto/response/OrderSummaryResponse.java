package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the order history. */
public record OrderSummaryResponse(
        UUID id,
        String invoiceNumber,
        OrderStatus status,
        Long totalAmount,
        int itemCount,
        String firstProductName,
        Instant createdAt
) {
}
