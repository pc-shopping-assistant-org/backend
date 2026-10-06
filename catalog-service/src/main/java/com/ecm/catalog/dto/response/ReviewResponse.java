package com.ecm.catalog.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code reviewerName} is only filled in the public list; create/update answer the reviewer themself.
 */
public record ReviewResponse(
        UUID id,
        UUID productId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt,
        Instant editedAt
) {
}
