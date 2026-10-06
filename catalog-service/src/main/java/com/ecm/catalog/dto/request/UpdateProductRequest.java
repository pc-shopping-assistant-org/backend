package com.ecm.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A null images list keeps the gallery as it is; a list (even an empty one) replaces it. */
public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String seoName,
        UUID brandId,
        @NotNull UUID categoryId,
        Map<String, Object> specifications,
        String description,
        List<@Valid @NotNull ProductImageRequest> images
) {}
