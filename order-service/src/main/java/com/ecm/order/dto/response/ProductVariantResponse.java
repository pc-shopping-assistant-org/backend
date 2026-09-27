package com.ecm.order.dto.response;

import java.util.UUID;

/**
 * Mirrors catalog-service's variant response shape; duplicated here rather than shared.
 */
public record ProductVariantResponse(
        UUID id,
        Long listPrice,
        Integer quantity,
        String status
) {
}
