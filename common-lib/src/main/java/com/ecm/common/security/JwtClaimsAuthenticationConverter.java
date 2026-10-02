package com.ecm.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;

public class JwtClaimsAuthenticationConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }
        return authorities;
    }
}
