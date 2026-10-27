package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryDetailsRequest(@NotBlank @Size(max = 255) String name,
                                           @Size(max = 255) String seoName,
                                           String description,
                                           UUID parentId,
                                           @PositiveOrZero Integer displayOrder,
                                           String hardwareTypeCode) {
    public UpdateCategoryDetailsRequest(String name, String seoName, String description, UUID parentId) {
        this(name, seoName, description, parentId, null, null);
    }

    public UpdateCategoryDetailsRequest(String name, String seoName, String description, UUID parentId,
                                        Integer displayOrder) {
        this(name, seoName, description, parentId, displayOrder, null);
    }
}
