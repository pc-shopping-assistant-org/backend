package com.ecm.identity.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_ACCOUNT_ID = "accountId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_STATUS = "status";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecretKey());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID accountId, String email, String role, String status) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ACCOUNT_ID, accountId.toString())
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_STATUS, status)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID accountId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpirationMs());

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_ACCOUNT_ID, accountId.toString())
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getClaims(token).get(CLAIM_TYPE, String.class));
    }

    public UserPrincipal getUserPrincipal(String token) {
        Claims claims = getClaims(token);
        String accountIdStr = claims.get(CLAIM_ACCOUNT_ID, String.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        String status = claims.get(CLAIM_STATUS, String.class);

        List<GrantedAuthority> authorities = role != null
                ? Collections.singletonList(new SimpleGrantedAuthority(role))
                : Collections.emptyList();

        return UserPrincipal.builder()
                .accountId(accountIdStr != null ? UUID.fromString(accountIdStr) : null)
                .username(claims.getSubject())
                .role(role)
                .status(status)
                .authorities(authorities)
                .build();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token is expired: {}", ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
