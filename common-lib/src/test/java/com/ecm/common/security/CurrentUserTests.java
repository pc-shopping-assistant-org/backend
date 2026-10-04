package com.ecm.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrentUserTests {

    @Test
    void readsAccountIdFromVerifiedJwtAuthentication() {
        UUID accountId = UUID.randomUUID();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("accountId", accountId.toString()));
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);

        assertEquals(accountId, CurrentUser.accountId(authentication));
    }

    @Test
    void returnsNullForNonJwtOrMalformedAccountId() {
        assertNull(CurrentUser.accountId(null));
        assertNull(CurrentUser.accountId(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "pass")));

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), Map.of("accountId", "not-a-uuid"));
        assertNull(CurrentUser.accountId(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt)));
    }
}
