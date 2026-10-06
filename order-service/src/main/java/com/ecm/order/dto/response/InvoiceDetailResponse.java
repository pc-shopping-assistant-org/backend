package com.ecm.order.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An invoice, read from the snapshot of its completed order. */
public record InvoiceDetailResponse(
        UUID id,
        String invoiceNumber,
        Instant invoiceDate,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        List<OrderItemResponse> items,
        Long subtotalAmount,
        Long discountAmount,
        Long shippingFee,
        Long totalAmount
) {
}
