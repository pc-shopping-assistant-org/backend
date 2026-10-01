package com.ecm.order.dto.response;

import java.util.UUID;

public record ProductImageResponse(UUID id, UUID fileId, String url, boolean isMain) {
}
