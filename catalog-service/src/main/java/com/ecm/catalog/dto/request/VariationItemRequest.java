package com.ecm.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One variant of the list that replaces the variations of a product. With an id it is that existing variant (it keeps
 * its stock and order history, and {@code quantity} is ignored); without one it is created with {@code quantity} as its
 * opening stock.
 */
public record VariationItemRequest(
        UUID id,
        @NotNull @PositiveOrZero Long price,
        @PositiveOrZero Integer quantity,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 100) String model,
        String description,
        @NotNull @Positive Integer warrantyMonths,
        @Size(max = 100) String barcode,
        LocalDate releaseAt,
        UUID imageFileId,
        List<@Valid @NotNull VariantOptionRequest> options
) {}
