package com.ecm.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Every variant the product should sell from now on, saved at once so the option groups can change shape (a product
 * without groups gaining "Color", or the reverse) without passing through a state that breaks the rules on the way.
 */
public record SaveVariationsRequest(
        @NotEmpty List<@Valid @NotNull VariationItemRequest> variants
) {}
