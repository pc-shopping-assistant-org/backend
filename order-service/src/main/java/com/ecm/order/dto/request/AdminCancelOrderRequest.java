package com.ecm.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelOrderRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
        String reason) {
}
