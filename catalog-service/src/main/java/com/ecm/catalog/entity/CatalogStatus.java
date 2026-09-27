package com.ecm.catalog.entity;

/**
 * Shared by every Catalog Service table that has a status column — they all use the
 * same three values, only the underlying varchar length differs (a storage detail, not
 * a Java concern).
 */
public enum CatalogStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
