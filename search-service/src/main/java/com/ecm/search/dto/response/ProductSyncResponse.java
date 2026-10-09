package com.ecm.search.dto.response;

/**
 * Outcome of a full sync: products indexed from the catalog, stale documents removed, and products that failed
 * and need another run.
 */
public record ProductSyncResponse(int indexed, int removed, int failed) {
}
