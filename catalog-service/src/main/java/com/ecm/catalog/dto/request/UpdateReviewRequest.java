package com.ecm.catalog.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
        @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment
) {

    @AssertTrue(message = "rating or comment is required")
    public boolean isAnyFieldPresent() {
        return rating != null || comment != null;
    }
}
