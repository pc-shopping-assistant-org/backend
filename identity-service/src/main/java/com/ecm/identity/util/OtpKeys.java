package com.ecm.identity.util;

import java.util.Locale;
import java.util.UUID;

/** The Redis keys that hold the codes and pending data of the sign-up and password flows. */
public final class OtpKeys {

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String REG_DATA_KEY_PREFIX = "reg_data:";
    private static final String PWD_CHANGE_KEY_PREFIX = "pwd_change:";

    private OtpKeys() {
    }

    /** The code sent to an email for one purpose; email and purpose are matched case-insensitively. */
    public static String otp(String email, String purpose) {
        return OTP_KEY_PREFIX + purpose.toUpperCase(Locale.ROOT) + ":" + email.toLowerCase(Locale.ROOT);
    }

    /** The sign-up data held until the email is verified. */
    public static String registration(String email) {
        return REG_DATA_KEY_PREFIX + email.toLowerCase(Locale.ROOT);
    }

    /** The new password held until a password change is confirmed with a code. */
    public static String passwordChange(UUID accountId) {
        return PWD_CHANGE_KEY_PREFIX + accountId;
    }
}
