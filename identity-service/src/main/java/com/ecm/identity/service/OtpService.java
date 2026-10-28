package com.ecm.identity.service;

import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.util.OtpKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * One-time codes and the data waiting on them (a registration or a password change), kept in Redis so they expire on
 * their own. A code lives {@code OTP_TTL}; what it unlocks lives a little longer, so a user who types the code in
 * the last minute still finds their data.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_MIN_VALUE = 100_000;
    private static final int OTP_BOUND = 900_000;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration REGISTRATION_DATA_TTL = Duration.ofMinutes(10);
    private static final Duration PASSWORD_CHANGE_TTL = Duration.ofMinutes(10);
    /*
     * Compare and delete in one Redis step. Done as a GET then a DEL from here, two requests carrying the same code
     * could both pass the compare before either deleted it, and one code would then work twice.
     */
    private static final DefaultRedisScript<Long> OTP_CONSUME_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void generateAndSendOtp(String email, String purpose) {
        // 1. Six digits that never start with 0, from a secure source: a guessable code defeats the whole check
        String otp = String.valueOf(OTP_MIN_VALUE + secureRandom.nextInt(OTP_BOUND));

        // 2. Replace any earlier code for the same purpose, then mail the new one
        redisTemplate.opsForValue().set(OtpKeys.otp(email, purpose), otp, OTP_TTL);
        emailService.sendOtpEmail(email, otp, purpose);
    }

    /** Checks the code but leaves it valid, for a flow that has more to check before it spends the code. */
    public boolean verifyOtp(String email, String purpose, String otp) {
        String cachedOtp = redisTemplate.opsForValue().get(OtpKeys.otp(email, purpose));
        return cachedOtp != null && cachedOtp.equals(otp);
    }

    /** Checks the code and spends it in the same step, so it can be used once. */
    public boolean verifyAndConsumeOtp(String email, String purpose, String otp) {
        String key = OtpKeys.otp(email, purpose);
        Long result = redisTemplate.execute(OTP_CONSUME_SCRIPT, List.of(key), otp);
        return Long.valueOf(1).equals(result);
    }

    public void deleteOtp(String email, String purpose) {
        redisTemplate.delete(OtpKeys.otp(email, purpose));
    }

    public void savePendingRegistration(String email, RegisterRequest request) {
        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForValue().set(OtpKeys.registration(email), json, REGISTRATION_DATA_TTL);
    }

    public RegisterRequest getPendingRegistration(String email) {
        String json = redisTemplate.opsForValue().get(OtpKeys.registration(email));
        return json == null ? null : objectMapper.readValue(json, RegisterRequest.class);
    }

    public void deletePendingRegistration(String email) {
        redisTemplate.delete(OtpKeys.registration(email));
    }

    public void savePendingPasswordChange(UUID accountId, String newPassword) {
        redisTemplate.opsForValue().set(OtpKeys.passwordChange(accountId), newPassword, PASSWORD_CHANGE_TTL);
    }

    public String getPendingPasswordChange(UUID accountId) {
        return redisTemplate.opsForValue().get(OtpKeys.passwordChange(accountId));
    }

    public void deletePendingPasswordChange(UUID accountId) {
        redisTemplate.delete(OtpKeys.passwordChange(accountId));
    }
}
