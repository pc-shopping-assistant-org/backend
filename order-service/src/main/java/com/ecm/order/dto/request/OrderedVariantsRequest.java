package com.ecm.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The variants the Catalog Service asks about before deleting or changing them. */
public record OrderedVariantsRequest(
        @NotNull @Size(max = OrderedVariantsRequest.MAX_VARIANT_IDS) List<@NotNull UUID> variantIds
) {

    /** Far above the variants of one product, the most the Catalog Service asks about at once. */
    public static final int MAX_VARIANT_IDS = 1000;
}
