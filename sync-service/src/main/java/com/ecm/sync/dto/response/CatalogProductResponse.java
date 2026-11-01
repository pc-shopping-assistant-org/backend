package com.ecm.sync.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
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
        Map<String, Object> specifications,
        List<Image> images,
        List<Variant> variants) {

    public CatalogProductResponse(
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
        this(id, name, seoName, brandId, brandName, categoryId, categoryName, description, Map.of(), images, variants);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url, boolean main) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(UUID id, String sku, String model, Long price, Integer quantity, List<Option> options) {
        public Variant(Long price, Integer quantity) {
            this(null, null, null, price, quantity, List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String name, String value) {
    }
}
