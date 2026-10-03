package com.ecm.identity.service;

import com.ecm.identity.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_MIN_VALUE = 100_000;
    private static final int OTP_BOUND = 900_000;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration REGISTRATION_DATA_TTL = Duration.ofMinutes(10);
    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String REG_DATA_KEY_PREFIX = "reg_data:";
    private static final String OTP_CONSUME_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void generateAndSendOtp(String email, String purpose) {
        String otp = String.valueOf(OTP_MIN_VALUE + secureRandom.nextInt(OTP_BOUND));
        redisTemplate.opsForValue().set(buildOtpKey(email, purpose), otp, OTP_TTL);
        emailService.sendOtpEmail(email, otp, purpose);
    }

    public boolean verifyOtp(String email, String purpose, String otp) {
        String cachedOtp = redisTemplate.opsForValue().get(buildOtpKey(email, purpose));
        return cachedOtp != null && cachedOtp.equals(otp);
    }

    public boolean verifyAndConsumeOtp(String email, String purpose, String otp) {
        String key = buildOtpKey(email, purpose);
        Long result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(OTP_CONSUME_SCRIPT, Long.class),
                List.of(key),
                otp);
        return Long.valueOf(1).equals(result);
    }

    public void deleteOtp(String email, String purpose) {
        redisTemplate.delete(buildOtpKey(email, purpose));
    }

    public void savePendingRegistration(String email, RegisterRequest request) {
        String json = objectMapper.writeValueAsString(request);
        redisTemplate.opsForValue().set(buildRegDataKey(email), json, REGISTRATION_DATA_TTL);
    }

    public RegisterRequest getPendingRegistration(String email) {
        String json = redisTemplate.opsForValue().get(buildRegDataKey(email));
        return json == null ? null : objectMapper.readValue(json, RegisterRequest.class);
    }

    public void deletePendingRegistration(String email) {
        redisTemplate.delete(buildRegDataKey(email));
    }

    private String buildOtpKey(String email, String purpose) {
        return OTP_KEY_PREFIX + purpose.toUpperCase() + ":" + email.toLowerCase();
    }

    private String buildRegDataKey(String email) {
        return REG_DATA_KEY_PREFIX + email.toLowerCase();
    }
}
