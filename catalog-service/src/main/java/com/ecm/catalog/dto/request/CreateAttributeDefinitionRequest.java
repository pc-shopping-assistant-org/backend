package com.ecm.catalog.dto.request;

import com.ecm.catalog.entity.AttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAttributeDefinitionRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "key must be lower snake_case") String key,
        @NotBlank @Size(max = 255) String displayName,
        @NotNull AttributeDataType dataType,
        @Size(max = 50) String unit,
        List<@NotBlank String> allowedValues,
        List<@NotBlank String> aliases,
        Boolean filterable,
        Boolean comparable
) {}
