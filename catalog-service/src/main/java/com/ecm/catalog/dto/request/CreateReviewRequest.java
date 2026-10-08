package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReviewRequest(
        @NotNull UUID orderItemId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment
) {
}
