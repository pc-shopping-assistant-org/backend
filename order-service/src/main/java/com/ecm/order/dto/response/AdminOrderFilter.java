package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminOrderFilter(String keyword, OrderStatus status, Instant createdFrom, Instant createdTo, UUID customerId) {}
