package com.ecm.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record ReorderCategoriesRequest(
        @NotEmpty List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotNull UUID id,
            @NotNull @PositiveOrZero Integer displayOrder
    ) {}
}
