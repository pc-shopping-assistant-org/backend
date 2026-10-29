package com.ecm.identity.util;

import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.identity.exception.IdentityErrorCode;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the unique violation of an account insert or update into the error that names the field that clashed.
 * Two concurrent requests can pass the "already taken" checks together, so the database is the last judge.
 */
public final class AccountDuplicates {

    private static final String PHONE_CONSTRAINT_MARKER = "phone";
    private static final Pattern VIOLATED_CONSTRAINT = Pattern.compile("constraint \"([^\"]+)\"");

    private AccountDuplicates() {
    }

    /** PHONE_ALREADY_EXISTS when the violated constraint is the phone one, EMAIL_ALREADY_EXISTS otherwise. */
    public static DuplicateResourceException from(DataIntegrityViolationException violation, String email, String phone) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(violation);
        // Only the quoted constraint name decides: the DETAIL line that follows repeats the value, which may say "phone"
        Matcher matcher = VIOLATED_CONSTRAINT.matcher(cause.getMessage() == null ? "" : cause.getMessage());
        if (matcher.find() && matcher.group(1).toLowerCase(Locale.ROOT).contains(PHONE_CONSTRAINT_MARKER)) {
            return new DuplicateResourceException(IdentityErrorCode.PHONE_ALREADY_EXISTS, "Account", "phone", phone);
        }
        return new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", email);
    }
}
