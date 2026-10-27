package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String seoName,
        String description,
        UUID parentId,
        @PositiveOrZero Integer displayOrder,
        @NotNull CatalogStatus status,
        String hardwareTypeCode
) {
    public UpdateCategoryRequest(String name, String seoName, String description, UUID parentId, CatalogStatus status) {
        this(name, seoName, description, parentId, null, status, null);
    }

    public UpdateCategoryRequest(String name, String seoName, String description, UUID parentId,
                                 Integer displayOrder, CatalogStatus status) {
        this(name, seoName, description, parentId, displayOrder, status, null);
    }
}
