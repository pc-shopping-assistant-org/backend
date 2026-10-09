package com.ecm.search.dto.response;

import java.util.UUID;

public record ProductSearchResponse(
        UUID id,
        String name,
        String seoName,
        String brandName,
        String categoryName,
        Long minPrice,
        Long maxPrice,
        boolean inStock,
        String mainImageUrl) {
}
