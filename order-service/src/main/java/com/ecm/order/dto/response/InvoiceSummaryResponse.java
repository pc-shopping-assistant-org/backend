package com.ecm.order.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One row of the invoice list: a completed order. */
public record InvoiceSummaryResponse(
        UUID id,
        String invoiceNumber,
        String recipientName,
        Long totalAmount,
        Instant invoiceDate
) {
}
