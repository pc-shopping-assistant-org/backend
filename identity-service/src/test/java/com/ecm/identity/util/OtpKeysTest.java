package com.ecm.identity.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OtpKeysTest {

    @Test
    void theOtpKeyIgnoresTheCaseOfTheEmailAndThePurpose() {
        assertEquals("otp:REGISTER:a@b.com", OtpKeys.otp("A@B.com", "register"));
    }

    @Test
    void theRegistrationKeyIgnoresTheCaseOfTheEmail() {
        assertEquals("reg_data:a@b.com", OtpKeys.registration("A@B.com"));
    }

    @Test
    void thePasswordChangeKeyIsPerAccount() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        assertEquals("pwd_change:" + id, OtpKeys.passwordChange(id));
    }
}
