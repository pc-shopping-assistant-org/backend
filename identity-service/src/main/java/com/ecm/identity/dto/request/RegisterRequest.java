package com.ecm.identity.dto.request;

import com.ecm.identity.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Phone number must be a valid Vietnamese phone number")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one number")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        Gender gender,

        LocalDate birthday,

        @NotBlank(message = "Address is required")
        @Size(max = 500, message = "Address cannot exceed 500 characters")
        String address) {
}
