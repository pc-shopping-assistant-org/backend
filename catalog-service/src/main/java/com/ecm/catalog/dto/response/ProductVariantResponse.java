package com.ecm.catalog.dto.response;

import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        Long listPrice,
        Integer quantity,
        String status) {
}
