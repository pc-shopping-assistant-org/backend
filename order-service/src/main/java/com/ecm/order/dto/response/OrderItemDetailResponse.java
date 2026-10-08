package com.ecm.order.dto.response;

import com.ecm.order.entity.OrderStatus;

import java.util.UUID;

/**
 * What another service needs to know about an order line, e.g. catalog-service before accepting a review.
 */
public record OrderItemDetailResponse(
        UUID id,
        UUID orderId,
        OrderStatus orderStatus,
        UUID productVariantId
) {
}
