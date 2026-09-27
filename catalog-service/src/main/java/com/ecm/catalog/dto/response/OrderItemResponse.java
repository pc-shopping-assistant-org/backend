package com.ecm.catalog.dto.response;

import java.util.UUID;

/**
 * Mirrors order-service's response shape; duplicated here rather than shared.
 */
public record OrderItemResponse(
        UUID id,
        UUID orderId,
        String orderStatus
) {
}
