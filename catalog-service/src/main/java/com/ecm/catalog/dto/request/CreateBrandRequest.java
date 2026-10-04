package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBrandRequest(
        @NotBlank @Size(max = 255) String name,
        String seoName,
        String description,
        java.util.UUID imageFileId
) {}
