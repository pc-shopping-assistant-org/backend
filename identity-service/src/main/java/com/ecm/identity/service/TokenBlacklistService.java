package com.ecm.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    private final StringRedisTemplate redisTemplate;

    public boolean tryBlacklistToken(String token, long expirationSeconds) {
        if (expirationSeconds <= 0) {
            return true;
        }
        try {
            redisTemplate.opsForValue().set(buildKey(token), "revoked", Duration.ofSeconds(expirationSeconds));
            return true;
        } catch (org.springframework.data.redis.RedisConnectionFailureException
                 | org.springframework.data.redis.RedisSystemException ex) {
            return false;
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(token)));
    }

    private String buildKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return BLACKLIST_PREFIX + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
