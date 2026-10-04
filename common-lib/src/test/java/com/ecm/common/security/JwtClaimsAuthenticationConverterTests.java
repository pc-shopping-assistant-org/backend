package com.ecm.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtClaimsAuthenticationConverterTests {

    private final JwtClaimsAuthenticationConverter converter = new JwtClaimsAuthenticationConverter();

    @Test
    void mapsRoleClaimToSpringRoleAuthority() {
        Jwt jwt = jwtWithClaims(Map.of("role", "ROLE_CUSTOMER", "scope", "read write"));

        var authorities = converter.convert(jwt);

        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("SCOPE_read")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("SCOPE_write")));
    }

    @Test
    void addsRolePrefixWhenRoleClaimDoesNotContainOne() {
        var authorities = converter.convert(jwtWithClaims(Map.of("role", "ADMIN")));

        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void missingRoleDoesNotAddRoleAuthority() {
        var authorities = converter.convert(jwtWithClaims(Map.of("aud", "pc-shopping-api")));

        assertEquals(0, authorities.size());
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }
}
