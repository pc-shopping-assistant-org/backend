package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        String seoName,
        String description,
        java.util.UUID parentId,
        @PositiveOrZero Integer displayOrder,
        String hardwareTypeCode
) {
    public CreateCategoryRequest(String name, String seoName, String description, java.util.UUID parentId) {
        this(name, seoName, description, parentId, null, null);
    }

    public CreateCategoryRequest(String name, String seoName, String description, java.util.UUID parentId,
                                 Integer displayOrder) {
        this(name, seoName, description, parentId, displayOrder, null);
    }
}
