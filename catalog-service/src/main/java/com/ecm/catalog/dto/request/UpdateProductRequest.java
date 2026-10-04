package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String seoName,
        UUID brandId,
        @NotNull UUID categoryId,
        Map<String, Object> specifications,
        String description,
        @NotNull CatalogStatus status,
        List<UUID> supplierIds
) {
    public record VariantRequest(
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
}
