package com.ecm.search.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Mirrors the part of catalog-service's product response the index needs; duplicated here rather than shared.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProductResponse(
        UUID id,
        String name,
        String seoName,
        UUID brandId,
        String brandName,
        UUID categoryId,
        String categoryName,
        String description,
        List<Image> images,
        List<Variant> variants) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url, boolean main) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(Long price, Integer quantity) {
    }
}
