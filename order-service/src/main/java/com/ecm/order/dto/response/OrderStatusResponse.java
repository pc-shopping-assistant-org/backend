package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusResponse(UUID id, String invoiceNumber, OrderStatus status, Instant createdAt, Instant updatedAt) {
}
