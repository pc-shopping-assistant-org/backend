package com.ecm.payment.entity;

public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    /** The order was cancelled before the payment was settled. */
    CANCELLED,
    /** A paid payment that was returned to the customer by hand. */
    REFUNDED
}
