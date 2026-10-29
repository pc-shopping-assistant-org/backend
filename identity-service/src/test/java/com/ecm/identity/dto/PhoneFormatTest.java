package com.ecm.identity.dto;

import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.request.UpdateProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The same number has to pass the sign-up form and the profile form, or a customer cannot save what they registered with. */
class PhoneFormatTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean registerRejects(String phone) {
        return validator.validate(new RegisterRequest("a@b.co", phone, "Passw0rdA", "An", "Test", null, null, "1 Test St")).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("phone"));
    }

    private boolean profileRejects(String phone) {
        return validator.validate(new UpdateProfileRequest("An", "Test", null, null, phone, null)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("phone"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0912345678", "09123456789", "+84912345678", "+849123456789"})
    void bothFormsAcceptTheSameNumbers(String phone) {
        assertEquals(false, registerRejects(phone), "register: " + phone);
        assertEquals(false, profileRejects(phone), "profile: " + phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"091234567", "abc", "0912 345 678", "84912345678"})
    void bothFormsRejectTheSameNumbers(String phone) {
        assertEquals(true, registerRejects(phone), "register: " + phone);
        assertEquals(true, profileRejects(phone), "profile: " + phone);
    }
}
