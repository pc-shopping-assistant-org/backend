package com.ecm.identity.service.google;

import org.springframework.util.StringUtils;

/**
 * Verified Google identity extracted from an ID token.
 *
 * @param subject    the stable Google user identifier (sub claim)
 * @param email      the verified email address
 * @param givenName  the first name Google holds, or null
 * @param familyName the last name Google holds, or null
 * @param fullName   the display name Google holds, or null
 * @param pictureUrl the profile picture address, or null
 */
public record GoogleIdentity(String subject, String email, String givenName, String familyName, String fullName,
                             String pictureUrl) {

    private static final int MAX_NAME_LENGTH = 100;

    /** Google's given name, else the last word of the full name, else the part of the email before the @. */
    public String firstName() {
        if (StringUtils.hasText(givenName)) {
            return limit(givenName);
        }
        if (StringUtils.hasText(fullName)) {
            String full = fullName.trim();
            return limit(full.substring(full.lastIndexOf(' ') + 1));
        }
        return limit(email.substring(0, email.indexOf('@')));
    }

    /** Google's family name, else the rest of the full name once the last word is the first name, else empty. */
    public String lastName() {
        if (StringUtils.hasText(familyName)) {
            return limit(familyName);
        }
        if (!StringUtils.hasText(givenName) && StringUtils.hasText(fullName)) {
            String full = fullName.trim();
            int cut = full.lastIndexOf(' ');
            return cut < 0 ? "" : limit(full.substring(0, cut));
        }
        return "";
    }

    private static String limit(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }
}
