package com.ecm.media.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MediaFileResponse(
        UUID id,
        String originalName,
        String mimeType,
        Long sizeBytes,
        String url,
        Instant createdAt
) {
}
