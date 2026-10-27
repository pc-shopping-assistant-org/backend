package com.ecm.common.security;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

public final class CurrentUser {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCOUNT_ID_CLAIM = "accountId";

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
        String value = token.getToken().getClaimAsString(ACCOUNT_ID_CLAIM);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The account id of the caller; a caller without a valid one is not signed in, so the request is refused with 401. */
    public static UUID requireAccountId(Authentication authentication) {
        return requireAccountId(authentication, CommonErrorCode.UNAUTHORIZED);
    }

    /** Same, for a service whose use case has its own error for a missing caller. */
    public static UUID requireAccountId(Authentication authentication, ErrorCode missing) {
        UUID accountId = accountId(authentication);
        if (accountId == null) {
            throw new BusinessException(missing);
        }
        return accountId;
    }
}
