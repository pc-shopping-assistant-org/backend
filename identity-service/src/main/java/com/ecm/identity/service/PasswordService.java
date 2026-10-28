package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExpiredException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** Forgotten and changed passwords, each confirmed with a code sent by email: UC-AUTH-003, 005. */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final String OTP_PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String OTP_PURPOSE_PASSWORD_CHANGE = "PASSWORD_CHANGE";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        // 1. Generate a uniform response path and only send when a usable account exists
        accountRepository.findByEmailIgnoreCase(normalized)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(_ -> otpService.generateAndSendOtp(normalized, OTP_PURPOSE_PASSWORD_RESET));
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        // 1. Confirm that the OTP matches an active account before consuming it
        Account account = accountRepository.findByEmailIgnoreCase(normalized)
                .filter(existing -> existing.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_OTP));
        if (!otpService.verifyAndConsumeOtp(normalized, OTP_PURPOSE_PASSWORD_RESET, otp)) {
            throw new BusinessException(IdentityErrorCode.INVALID_OTP);
        }

        // 2. Update the password
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }

    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        // 1. Retrieve an active account
        Account account = activeAccount(accountId);

        // 2. Verify the current password before sending the confirmation OTP; a Google-only account has none yet,
        // and the OTP sent to its email is then the only proof asked for
        if (account.getPasswordHash() != null
                && (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash()))) {
            throw new BusinessException(IdentityErrorCode.INCORRECT_PASSWORD);
        }

        // 3. Cache the new password pending OTP verification, then email the OTP
        otpService.savePendingPasswordChange(accountId, newPassword);
        otpService.generateAndSendOtp(account.getEmail(), OTP_PURPOSE_PASSWORD_CHANGE);
    }

    @Transactional
    public void verifyPasswordChangeOtp(UUID accountId, String otp) {
        // 1. Confirm the OTP before consuming it and reading the pending password
        Account account = activeAccount(accountId);
        if (!otpService.verifyAndConsumeOtp(account.getEmail(), OTP_PURPOSE_PASSWORD_CHANGE, otp)) {
            throw new BusinessException(IdentityErrorCode.INVALID_OTP);
        }

        // 2. Retrieve the pending password cached at step 3 of changePassword()
        String newPassword = otpService.getPendingPasswordChange(accountId);
        if (newPassword == null) {
            throw new ExpiredException("Password change session", accountId);
        }

        // 3. Apply the new password and clear the consumed cache entry
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        otpService.deletePendingPasswordChange(accountId);
    }

    private Account activeAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .filter(existing -> existing.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }
}
