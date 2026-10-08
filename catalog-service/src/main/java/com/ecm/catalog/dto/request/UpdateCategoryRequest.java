package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.CatalogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String seoName,
        String description,
        UUID parentId,
        @NotNull CatalogStatus status
) {}
