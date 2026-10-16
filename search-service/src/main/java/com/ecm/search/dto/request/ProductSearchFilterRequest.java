package com.ecm.search.dto.request;

import java.util.UUID;

/**
 * What narrows a search, all optional. A category also matches the products of its sub-categories, and a price range
 * matches a product when at least one of its variants is priced inside it, as in the catalog's own filter.
 */
public record ProductSearchFilterRequest(UUID categoryId, UUID brandId, Long minPrice, Long maxPrice) {
}
