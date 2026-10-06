package com.ecm.identity.dto.request;

import com.ecm.identity.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Every field is optional; a field left out keeps its current value.
 */
public record UpdateEmployeeRequest(

        @Email(message = "Email must be a valid email address")
        String email,

        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Phone number must be a valid Vietnamese phone number")
        String phone,

        @Pattern(regexp = ".*\\S.*", message = "First name cannot be blank")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @Pattern(regexp = ".*\\S.*", message = "Last name cannot be blank")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        Gender gender,

        @Past(message = "Birthday must be in the past")
        LocalDate birthday,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address,

        UUID avatarFileId) {
}
