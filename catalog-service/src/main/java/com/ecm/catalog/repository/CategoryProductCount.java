package com.ecm.catalog.repository;

import java.util.UUID;

/** The number of products listed in one category, without its sub-categories. */
public record CategoryProductCount(UUID categoryId, long count) {
}
