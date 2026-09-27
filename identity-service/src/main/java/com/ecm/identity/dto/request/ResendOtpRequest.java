package com.ecm.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email) {
}
