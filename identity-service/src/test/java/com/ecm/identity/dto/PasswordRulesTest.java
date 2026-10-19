package com.ecm.identity.dto;

import com.ecm.identity.dto.request.ChangePasswordRequest;
import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.request.ResetPasswordRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordRulesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean changeRejects(String password) {
        return validator.validate(new ChangePasswordRequest(null, password)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("newPassword"));
    }

    private boolean resetRejects(String password) {
        return validator.validate(new ResetPasswordRequest("a@b.co", "123456", password)).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("newPassword"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "abcdefgh", "ABCDEFGH", "abcd1234", "ABCD1234", "Abcdefgh", "Ab1"})
    void weakPasswordsAreRejectedWhenChangedOrReset(String password) {
        assertTrue(changeRejects(password), "change: " + password);
        assertTrue(resetRejects(password), "reset: " + password);
    }

    @Test
    void aPasswordWithUpperLowerAndDigitIsAccepted() {
        assertFalse(changeRejects("Passw0rdA"));
        assertFalse(resetRejects("Passw0rdA"));
    }
}
