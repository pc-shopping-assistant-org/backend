package com.ecm.payment.dto.response;

/** Where to send the customer to pay on VNPAY. */
public record VnpayUrlResponse(String paymentUrl) {
}
