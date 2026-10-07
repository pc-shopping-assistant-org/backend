package com.ecm.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class CurrentUser {

    private static final String BEARER_PREFIX = "Bearer ";

    private CurrentUser() {
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    /** The token of the caller as an {@code Authorization} header value, to relay to a service called on their behalf. */
    public static String bearerToken(Authentication authentication) {
        return BEARER_PREFIX + ((JwtAuthenticationToken) authentication).getToken().getTokenValue();
    }

    public static UUID accountId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return null;
        }
        String value = token.getToken().getClaimAsString("accountId");
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
