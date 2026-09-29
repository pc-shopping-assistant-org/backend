package com.ecm.identity.service.google;

/**
 * Verified Google identity extracted from an ID token.
 *
 * @param subject the stable Google user identifier (sub claim)
 * @param email   the verified email address
 */
public record GoogleIdentity(String subject, String email) {
}
