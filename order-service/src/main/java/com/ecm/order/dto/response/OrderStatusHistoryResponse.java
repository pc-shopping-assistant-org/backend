package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** {@code changedBy} is the employee who made the change, null when the customer or the system did. */
public record OrderStatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, UUID changedBy, String reason, Instant createdAt) {
}
