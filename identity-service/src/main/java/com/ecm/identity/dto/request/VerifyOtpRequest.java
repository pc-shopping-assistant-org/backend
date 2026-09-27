package com.ecm.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be a 6-digit number")
        String otp) {
}
