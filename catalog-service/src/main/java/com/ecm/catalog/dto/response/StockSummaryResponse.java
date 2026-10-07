package com.ecm.catalog.dto.response;

/**
 * The catalog side of the admin dashboard, over active variants: those running low (1 up to the threshold units)
 * and, counted apart, those sold out.
 */
public record StockSummaryResponse(long lowStockVariants, long outOfStockVariants, int lowStockThreshold) {
}
