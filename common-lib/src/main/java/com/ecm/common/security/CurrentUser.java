package com.ecm.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
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
