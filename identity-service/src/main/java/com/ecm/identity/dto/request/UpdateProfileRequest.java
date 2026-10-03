package com.ecm.identity.dto.request;

import com.ecm.identity.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 50, message = "First name must be between 1 and 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 50, message = "Last name must be between 1 and 50 characters")
        String lastName,

        Gender gender,

        @Past(message = "Birthday must be in the past")
        LocalDate birthday,

        @Pattern(regexp = "^(\\+84|0)[0-9]{9}$", message = "Invalid phone number format")
        String phone
) {
}
