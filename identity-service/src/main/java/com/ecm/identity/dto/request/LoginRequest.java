package com.ecm.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email or phone number is required")
        String identifier,

        @NotBlank(message = "Password is required")
        String password) {
}
