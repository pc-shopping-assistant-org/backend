package com.ecm.identity.util;

import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.identity.exception.IdentityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountDuplicatesTest {

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new IllegalStateException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void aClashOnThePhoneIsReportedAsThePhone() {
        DuplicateResourceException ex = AccountDuplicates.from(violationOf("accounts_phone_key"), "an@example.com", "0912345678");

        assertEquals(IdentityErrorCode.PHONE_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void aClashOnTheEmailIsReportedAsTheEmail() {
        DuplicateResourceException ex = AccountDuplicates.from(violationOf("accounts_email_key"), "an@example.com", "0912345678");

        assertEquals(IdentityErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void aValueThatSaysPhoneDoesNotMakeAnEmailClashLookLikeThePhone() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("could not execute statement",
                new IllegalStateException("ERROR: duplicate key value violates unique constraint \"accounts_email_key\"\n"
                        + "  Detail: Key (email)=(phone.shop@example.com) already exists."));

        DuplicateResourceException ex = AccountDuplicates.from(violation, "phone.shop@example.com", "0912345678");

        assertEquals(IdentityErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    void anUnreadableCauseFallsBackToTheEmail() {
        DuplicateResourceException ex = AccountDuplicates.from(new DataIntegrityViolationException("boom"), "an@example.com", "0912345678");

        assertEquals(IdentityErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }
}
