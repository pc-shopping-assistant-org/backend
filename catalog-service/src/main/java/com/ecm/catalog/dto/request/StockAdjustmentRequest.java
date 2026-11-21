package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotNull;

/** How much stock to add (a delivery: +20) or take off (a broken unit: -1). */
public record StockAdjustmentRequest(
        @NotNull Integer delta
) {}
