package com.ecm.search.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * One cursor page of the catalog's public product list; only the ids are needed, each product is then read in full.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProductPage(List<Item> items, boolean hasNext, String nextCursor) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(UUID id) {
    }
}
