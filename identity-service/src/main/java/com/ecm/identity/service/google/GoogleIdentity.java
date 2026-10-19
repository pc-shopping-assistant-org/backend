package com.ecm.identity.service.google;

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
}
