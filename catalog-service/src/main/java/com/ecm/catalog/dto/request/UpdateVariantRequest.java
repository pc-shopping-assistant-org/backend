package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateVariantRequest(
        @PositiveOrZero long listPrice,
        @PositiveOrZero int quantity,
        @NotBlank @Size(max = 100) String sku,
        @Size(max = 100) String model,
        String description,
        @Positive int warrantyMonths,
        @Size(max = 100) String barcode,
        LocalDate releaseAt,
        List<CreateProductRequest.ImageRequest> images,
        List<UUID> optionIds
) {}
