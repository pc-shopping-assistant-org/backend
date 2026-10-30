package com.ecm.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The variants the Catalog Service asks about before deleting or changing them. */
public record OrderedVariantsRequest(
        @NotNull @Size(max = 1000) List<@NotNull UUID> variantIds
) {}
