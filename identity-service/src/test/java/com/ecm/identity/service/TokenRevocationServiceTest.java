package com.ecm.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenRevocationServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final Instant CUTOFF = Instant.parse("2026-10-06T00:00:00Z");

    @SuppressWarnings("unchecked")
    @Test
    void tokenIssuedBeforeCutoffIsRevoked() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("revoked-before:account:" + ACCOUNT_ID)).thenReturn(String.valueOf(CUTOFF.getEpochSecond()));
        TokenRevocationService service = new TokenRevocationService(redisTemplate);

        assertTrue(service.isRevoked(ACCOUNT_ID, CUTOFF.minusSeconds(1)));
        assertFalse(service.isRevoked(ACCOUNT_ID, CUTOFF));
        assertFalse(service.isRevoked(ACCOUNT_ID, CUTOFF.plusSeconds(1)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void noMarkerMeansNotRevoked() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("revoked-before:account:" + ACCOUNT_ID)).thenReturn(null);
        TokenRevocationService service = new TokenRevocationService(redisTemplate);

        assertFalse(service.isRevoked(ACCOUNT_ID, CUTOFF.minusSeconds(60)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void revokeBeforeWritesMarkerWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TokenRevocationService service = new TokenRevocationService(redisTemplate);
        Duration ttl = Duration.ofDays(7);

        service.revokeBefore(ACCOUNT_ID, CUTOFF, ttl);

        verify(valueOperations).set("revoked-before:account:" + ACCOUNT_ID,
                String.valueOf(CUTOFF.getEpochSecond()), ttl);
    }
}
