package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Key and data type are immutable: stored product specifications are keyed and typed by them. */
public record UpdateAttributeDefinitionRequest(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 50) String unit,
        List<@NotBlank String> allowedValues,
        List<@NotBlank String> aliases,
        Boolean filterable,
        Boolean comparable
) {}
