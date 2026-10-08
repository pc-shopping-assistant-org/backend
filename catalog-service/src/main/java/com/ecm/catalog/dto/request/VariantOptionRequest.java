package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An option such as name "Color", value "Blue". An existing option with the same name and value is reused. */
public record VariantOptionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String value
) {}
