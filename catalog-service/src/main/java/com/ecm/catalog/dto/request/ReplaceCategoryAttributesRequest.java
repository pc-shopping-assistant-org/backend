package com.ecm.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Full replacement of a category attribute template. A null displayOrder falls back to the list position. */
public record ReplaceCategoryAttributesRequest(@NotNull @Valid List<@Valid Group> groups) {

    public record Group(
            @NotBlank @Size(max = 100) String name,
            @PositiveOrZero Integer displayOrder,
            @NotNull @Valid List<@Valid Item> attributes
    ) {}

    public record Item(
            @NotNull UUID attributeId,
            Boolean required,
            @PositiveOrZero Integer displayOrder
    ) {}
}
