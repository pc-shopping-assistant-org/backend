package com.ecm.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    PENDING_CONFIRMATION,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED
}
