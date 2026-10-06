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

public record CreateVariantRequest(
        @NotNull @PositiveOrZero Long price,
        @NotNull @PositiveOrZero Integer quantity,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 100) String model,
        String description,
        @NotNull @Positive Integer warrantyMonths,
        @Size(max = 100) String barcode,
        LocalDate releaseAt,
        UUID imageFileId,
        List<@Valid @NotNull VariantOptionRequest> options
) {}
