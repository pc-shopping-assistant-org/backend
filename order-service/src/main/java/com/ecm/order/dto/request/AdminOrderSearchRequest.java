package com.ecm.order.dto.request;

import com.ecm.order.entity.OrderStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AdminOrderSearchRequest(
        @Size(max = 100) String keyword,
        OrderStatus status,
        Instant createdFrom,
        Instant createdTo,
        UUID customerId
) {}
