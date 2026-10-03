package com.ecm.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void verifyAndConsumeOtpReturnsTrueOnlyWhenRedisScriptDeletesMatchingOtp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("otp:PASSWORD_RESET:user@example.com")), eq("123456")))
                .thenReturn(1L);
        OtpService otpService = new OtpService(redisTemplate, null, null);

        assertTrue(otpService.verifyAndConsumeOtp("user@example.com", "PASSWORD_RESET", "123456"));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("otp:PASSWORD_RESET:user@example.com")), eq("123456"));

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("otp:PASSWORD_RESET:user@example.com")), eq("wrong")))
                .thenReturn(0L);
        assertFalse(otpService.verifyAndConsumeOtp("user@example.com", "PASSWORD_RESET", "wrong"));
    }
}
