package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ExpiredException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.request.GoogleLoginRequest;
import com.ecm.identity.dto.request.LoginRequest;
import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.request.ResendOtpRequest;
import com.ecm.identity.dto.request.VerifyOtpRequest;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.helper.CustomerRegistrar;
import com.ecm.identity.helper.GoogleAccountLinker;
import com.ecm.identity.helper.TokenIssuer;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.service.google.GoogleIdentity;
import com.ecm.identity.service.google.GoogleIdentityVerifier;
import com.ecm.identity.util.AccountChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String OTP_PURPOSE_REGISTRATION = "REGISTRATION";
    private static final String OTP_PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String OTP_PURPOSE_PASSWORD_CHANGE = "PASSWORD_CHANGE";

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final OtpService otpService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final CustomerMapper customerMapper;
    private final TokenRevocationService tokenRevocationService;
    private final TokenIssuer tokenIssuer;
    private final CustomerRegistrar customerRegistrar;
    private final GoogleAccountLinker googleAccountLinker;

    public void register(RegisterRequest request) {
        // 1. Normalize identity fields so later lookups/comparisons are case/whitespace-stable
        RegisterRequest normalized = request.normalized();

        // 2. Reject duplicates before caching anything
        if (accountRepository.existsByEmailIgnoreCase(normalized.email())) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", normalized.email());
        }
        if (accountRepository.existsByPhone(normalized.phone())) {
            throw new DuplicateResourceException(IdentityErrorCode.PHONE_ALREADY_EXISTS, "Account", "phone", normalized.phone());
        }

        // 3. Cache the registration payload pending OTP verification
        otpService.savePendingRegistration(normalized.email(), normalized);

        // 4. Generate and email the OTP
        otpService.generateAndSendOtp(normalized.email(), OTP_PURPOSE_REGISTRATION);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 1. Resolve the account by email or phone — same message for "not found" and "wrong
        // password" below so a failed login never reveals whether the identifier exists.
        Account account = accountRepository.findByLoginIdentifier(request.identifier().trim())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS));

        // 2. Reject a locked/inactive/deleted account before spending time on password hashing.
        AccountChecks.ensureUsable(account);

        // 3. Match the password.
        // A Google-only account has no password to match
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Resolve the role and issue a fresh token pair.
        return tokenIssuer.issueFor(account);
    }

    public void resendOtp(ResendOtpRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (otpService.getPendingRegistration(email) == null) {
            throw new ExpiredException("Registration session", email);
        }
        otpService.generateAndSendOtp(email, OTP_PURPOSE_REGISTRATION);
    }

    @Transactional
    public AuthResponse verifyRegistrationOtp(VerifyOtpRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Validate OTP against the cached value
        if (!otpService.verifyOtp(email, OTP_PURPOSE_REGISTRATION, request.otp())) {
            throw new BusinessException(IdentityErrorCode.INVALID_OTP);
        }

        // 2. Retrieve the registration payload cached at step 1 of register()
        RegisterRequest pending = otpService.getPendingRegistration(email);
        if (pending == null) {
            throw new ExpiredException("Registration session", email);
        }

        // 3. Resolve the default role every new customer account gets
        Role customerRole = roleRepository.findByName(ROLE_CUSTOMER)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));

        // 4. Persist account + customer profile + default address
        CustomerRegistrar.Registered created = customerRegistrar.register(pending, customerRole);

        // 5. Evict the now-consumed OTP and pending registration cache entries
        otpService.deleteOtp(email, OTP_PURPOSE_REGISTRATION);
        otpService.deletePendingRegistration(email);

        // 6. Issue the JWT access/refresh token pair
        UserSummaryResponse userSummary = customerMapper.toSummary(created.account(), customerRole, created.customer());
        return tokenIssuer.issue(created.account(), customerRole, userSummary);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        // 1. Only a valid, unexpired refresh token is accepted; an access token is not
        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 2. A logout (or lock) revokes every token issued before it, refresh tokens included
        UUID accountId = tokenProvider.getAccountId(refreshToken);
        Instant issuedAt = tokenProvider.getIssuedAt(refreshToken);
        if (accountId == null || issuedAt == null || tokenRevocationService.isRevoked(accountId, issuedAt)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 3. The account has to be usable still; its role may have changed since the last token
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS));
        AccountChecks.ensureUsable(account);
        return tokenIssuer.issueFor(account);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Verify Google ID token signature, issuer, audience and email_verified
        GoogleIdentity identity = googleIdentityVerifier.verify(request.idToken());

        // 2. Look up by stable Google subject if already linked
        Account account = accountRepository.findByGoogleSubject(identity.subject()).orElse(null);

        if (account == null) {
            // 3. First time: link to the local account with the same verified email, or sign up a new customer
            account = googleAccountLinker.linkOrSignUp(identity);
        } else {
            // Already linked — just validate usability
            AccountChecks.ensureUsable(account);
        }

        // 4. Issue token pair
        return tokenIssuer.issueFor(account);
    }

    public boolean logout(String accessToken) {
        // 1. Reject malformed, expired, or refresh tokens
        if (!tokenProvider.validateToken(accessToken) || tokenProvider.isRefreshToken(accessToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 2. Determine the account to revoke tokens for
        UUID accountId = tokenProvider.getAccountId(accessToken);
        if (accountId == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Revoke every token issued before this logout instant, for the refresh lifetime.
        // The +1s margin covers the token being revoked itself, whose iat is truncated to the
        // current second and would otherwise compare equal to the cutoff instead of before it.
        tokenRevocationService.revokeBefore(accountId, Instant.now().plusSeconds(1),
                Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs()));
        return true;
    }

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
        Account account = accountRepository.findById(accountId)
                .filter(existing -> existing.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

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
        Account account = accountRepository.findById(accountId)
                .filter(existing -> existing.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
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
}
