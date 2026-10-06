package com.ecm.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.ecm.common.security.ApiResponseSecurityHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Rejects a token issued before its account was logged out or locked. Identity-service writes the
 * {@code revoked-before} marker to Redis; this is the only place every downstream service shares, so
 * checking here is what makes logout and lock take effect outside identity-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokedTokenFilter extends OncePerRequestFilter {

    // Same key identity-service's TokenRevocationService writes; duplicated, not shared, like other cross-service contracts.
    private static final String REVOKED_BEFORE_PREFIX = "revoked-before:account:";
    private static final String ACCOUNT_ID_CLAIM = "accountId";

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication
                && isRevoked(authentication.getToken())) {
            // Answer 401 here rather than just dropping the authentication: on a public route the request would
            // still be forwarded with the revoked token in its Authorization header, which downstream services accept.
            SecurityContextHolder.clearContext();
            ApiResponseSecurityHandler.INSTANCE.commence(request, response,
                    new BadCredentialsException("Token has been revoked"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isRevoked(Jwt jwt) {
        String accountId = jwt.getClaimAsString(ACCOUNT_ID_CLAIM);
        Instant issuedAt = jwt.getIssuedAt();
        if (accountId == null || issuedAt == null) {
            return false;
        }
        String marker;
        try {
            marker = redisTemplate.opsForValue().get(REVOKED_BEFORE_PREFIX + accountId);
        } catch (RuntimeException ex) {
            // Fail closed: if the marker cannot be read, a revoked token must not slip through (the caller gets 401).
            log.warn("Token revocation store is unavailable, rejecting the token: {}", ex.getMessage());
            return true;
        }
        return marker != null && issuedAt.getEpochSecond() < Long.parseLong(marker);
    }
}
