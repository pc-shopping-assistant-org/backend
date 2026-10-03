package com.ecm.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrderRequest(
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
        String idempotencyKey,

        @NotNull(message = "Shipping method id is required")
        UUID shippingMethodId,

        @NotNull(message = "Payment method id is required")
        UUID paymentMethodId,

        @NotBlank(message = "Recipient name is required")
        @Size(max = 100, message = "Recipient name cannot exceed 100 characters")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Recipient phone must be a valid Vietnamese phone number")
        String recipientPhone,

        @NotBlank(message = "Delivery address is required")
        @Size(max = 500, message = "Delivery address cannot exceed 500 characters")
        String deliveryAddress,

        @Size(max = 50, message = "Discount code cannot exceed 50 characters")
        String discountCode,

        String note) {
}
