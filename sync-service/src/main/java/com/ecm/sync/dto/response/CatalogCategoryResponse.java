package com.ecm.sync.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * A category with its parent, enough to walk from a product's category up to the root.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogCategoryResponse(UUID id, UUID parentId) {
}
