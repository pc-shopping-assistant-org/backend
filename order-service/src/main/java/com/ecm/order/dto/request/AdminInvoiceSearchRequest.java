package com.ecm.order.dto.request;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code keyword} is an invoice number or the name of the customer on the order; the dates are the invoice date, that is the delivery date. */
public record AdminInvoiceSearchRequest(
        @Size(max = 100) String keyword,
        Instant invoiceFrom,
        Instant invoiceTo
) {
}
