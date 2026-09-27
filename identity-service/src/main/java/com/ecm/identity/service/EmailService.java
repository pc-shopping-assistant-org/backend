package com.ecm.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        // No SMTP account configured (local dev without MAIL_USERNAME/MAIL_PASSWORD) —
        // log the OTP instead of failing the request.
        if (!StringUtils.hasText(fromEmail)) {
            log.info("Mail sender not configured (MAIL_USERNAME is empty). OTP for [{}] ({}) is: {}", toEmail, purpose, otp);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("[PC Shopping] Mã xác thực OTP - " + purpose);
            message.setText("Xin chào,\n\n"
                    + "Mã xác thực OTP của bạn cho yêu cầu " + purpose + " là: " + otp + "\n"
                    + "Mã này có hiệu lực trong 5 phút.\n\n"
                    + "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.\n\n"
                    + "Trân trọng,\nPC Shopping");

            mailSender.send(message);
            log.info("OTP email successfully sent to [{}] for purpose [{}]", toEmail, purpose);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to [{}]: {}", toEmail, ex.getMessage());
        }
    }
}
