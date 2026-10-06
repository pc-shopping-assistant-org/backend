package com.ecm.identity.service;

import com.ecm.common.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Account-level JWT revocation. Logout writes a {@code revoked-before:<accountId>} marker
 * holding the cutoff epoch second; any token issued before that second is rejected. This
 * invalidates every outstanding access token for the account at once instead of
 * blacklisting individual tokens, and lives for the refresh-token lifetime so the marker
 * outlives the longest-lived credential it protects.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String REVOKED_BEFORE_PREFIX = "revoked-before:account:";

    private final StringRedisTemplate redisTemplate;

    public void revokeBefore(UUID accountId, Instant cutoff, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(buildKey(accountId), String.valueOf(cutoff.getEpochSecond()), ttl);
        } catch (org.springframework.data.redis.RedisConnectionFailureException
                 | org.springframework.data.redis.RedisSystemException ex) {
            throw new ExternalServiceException("redis", ex);
        }
    }

    public boolean isRevoked(UUID accountId, Instant issuedAt) {
        String marker = redisTemplate.opsForValue().get(buildKey(accountId));
        if (marker == null) {
            return false;
        }
        return issuedAt.getEpochSecond() < Long.parseLong(marker);
    }

    private String buildKey(UUID accountId) {
        return REVOKED_BEFORE_PREFIX + accountId;
    }
}
