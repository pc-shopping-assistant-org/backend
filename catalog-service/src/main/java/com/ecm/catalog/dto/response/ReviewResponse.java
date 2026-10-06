package com.ecm.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID productId,
        int rating,
        String comment,
        Instant createdAt
) {
}
