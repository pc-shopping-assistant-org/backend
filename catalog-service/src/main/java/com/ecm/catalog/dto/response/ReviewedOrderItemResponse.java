package com.ecm.catalog.dto.response;

import java.util.UUID;

/** An order line the caller has already reviewed, so the order page can offer the review instead of asking for one. */
public record ReviewedOrderItemResponse(
        UUID orderItemId,
        UUID productId,
        UUID reviewId
) {
}
