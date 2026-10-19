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
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.CustomerAddress;
import com.ecm.identity.entity.Role;
import com.ecm.identity.event.GoogleAvatarRequested;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.AdminMapper;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.AdminRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.service.google.GoogleIdentity;
import com.ecm.identity.service.google.GoogleIdentityVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

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
    private static final int MILLIS_IN_SECOND = 1000;
    private static final int MAX_NAME_LENGTH = 100;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final OtpService otpService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final CustomerMapper customerMapper;
    private final EmployeeMapper employeeMapper;
    private final AdminMapper adminMapper;
    private final TokenRevocationService tokenRevocationService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public void register(RegisterRequest request) {
        // 1. Normalize identity fields so later lookups/comparisons are case/whitespace-stable
        RegisterRequest normalized = normalize(request);

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
        ensureAccountUsable(account);

        // 3. Match the password.
        // A Google-only account has no password to match
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Resolve the role and issue a fresh token pair.
        Role role = roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        return issueTokenPair(account, role, buildUserSummary(account, role));
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Verify Google ID token signature, issuer, audience and email_verified
        GoogleIdentity identity = googleIdentityVerifier.verify(request.idToken());

        // 2. Look up by stable Google subject if already linked
        Account account = accountRepository.findByGoogleSubject(identity.subject()).orElse(null);

        if (account == null) {
            // 3. First time: link to the local account with the same verified email, or sign up a new customer
            account = accountRepository.findByEmailIgnoreCase(identity.email())
                    .map(existing -> linkGoogle(existing, identity))
                    .orElseGet(() -> signUpWithGoogle(identity));
        } else {
            // Already linked — just validate usability
            ensureAccountUsable(account);
        }

        // 4. Issue token pair
        Role role = roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        return issueTokenPair(account, role, buildUserSummary(account, role));
    }

    private Account linkGoogle(Account account, GoogleIdentity identity) {
        // Prevent re-linking if another Google identity is already bound
        if (StringUtils.hasText(account.getGoogleSubject())
                && !identity.subject().equals(account.getGoogleSubject())) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        ensureAccountUsable(account);

        account.setGoogleSubject(identity.subject());
        return accountRepository.save(account);
    }

    /** Creates a customer with no password and no phone; never any other role. */
    private Account signUpWithGoogle(GoogleIdentity identity) {
        Role customerRole = roleRepository.findByName(ROLE_CUSTOMER)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        try {
            // Its own transaction, so the picture import only starts once the signup is really saved
            return transactionTemplate.execute(status -> {
                Account account = accountRepository.save(Account.builder()
                        .email(identity.email())
                        .googleSubject(identity.subject())
                        .roleId(customerRole.getId())
                        .status(AccountStatus.ACTIVE)
                        .build());
                customerRepository.save(Customer.builder()
                        .accountId(account.getId())
                        .firstName(firstNameOf(identity))
                        .lastName(lastNameOf(identity))
                        .build());
                if (StringUtils.hasText(identity.pictureUrl())) {
                    eventPublisher.publishEvent(
                            new GoogleAvatarRequested(account.getId(), account.getEmail(), identity.pictureUrl()));
                }
                return account;
            });
        } catch (DataIntegrityViolationException ex) {
            // Another request signed this person up first (double click, two tabs): use theirs
            return accountRepository.findByGoogleSubject(identity.subject())
                    .or(() -> accountRepository.findByEmailIgnoreCase(identity.email()))
                    .map(existing -> linkGoogle(existing, identity))
                    .orElseThrow(() -> ex);
        }
    }

    /** Google's given name, else the last word of the full name, else the part of the email before the @. */
    private static String firstNameOf(GoogleIdentity identity) {
        if (StringUtils.hasText(identity.givenName())) {
            return limitName(identity.givenName());
        }
        if (StringUtils.hasText(identity.fullName())) {
            String full = identity.fullName().trim();
            return limitName(full.substring(full.lastIndexOf(' ') + 1));
        }
        return limitName(identity.email().substring(0, identity.email().indexOf('@')));
    }

    /** Google's family name, else the rest of the full name once the last word is the first name, else empty. */
    private static String lastNameOf(GoogleIdentity identity) {
        if (StringUtils.hasText(identity.familyName())) {
            return limitName(identity.familyName());
        }
        if (!StringUtils.hasText(identity.givenName()) && StringUtils.hasText(identity.fullName())) {
            String full = identity.fullName().trim();
            int cut = full.lastIndexOf(' ');
            return cut < 0 ? "" : limitName(full.substring(0, cut));
        }
        return "";
    }

    private static String limitName(String name) {
        String trimmed = name.trim();
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }

    private void ensureAccountUsable(Account account) {
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_LOCKED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private UserSummaryResponse buildUserSummary(Account account, Role role) {
        return customerRepository.findById(account.getId())
                .map(customer -> customerMapper.toSummary(account, role, customer))
                .or(() -> employeeRepository.findById(account.getId())
                        .map(employee -> employeeMapper.toSummary(account, role, employee)))
                .or(() -> adminRepository.findById(account.getId())
                        .map(admin -> adminMapper.toSummary(account, role, admin)))
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", account.getId()));
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
        AccountAndCustomer created = persistAccount(pending, customerRole);

        // 5. Evict the now-consumed OTP and pending registration cache entries
        otpService.deleteOtp(email, OTP_PURPOSE_REGISTRATION);
        otpService.deletePendingRegistration(email);

        // 6. Issue the JWT access/refresh token pair
        UserSummaryResponse userSummary = customerMapper.toSummary(created.account(), customerRole, created.customer());
        return issueTokenPair(created.account(), customerRole, userSummary);
    }

    private record AccountAndCustomer(Account account, Customer customer) {
    }

    private AccountAndCustomer persistAccount(RegisterRequest pending, Role role) {
        Account account = Account.builder()
                .email(pending.email())
                .phone(pending.phone())
                .passwordHash(passwordEncoder.encode(pending.password()))
                .roleId(role.getId())
                .status(AccountStatus.ACTIVE)
                .build();
        try {
            account = accountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", pending.email());
        }

        Customer customer = customerMapper.toEntity(pending);
        customer.setAccountId(account.getId());
        customer = customerRepository.save(customer);

        customerAddressRepository.save(CustomerAddress.builder()
                .customerId(customer.getAccountId())
                .recipientName(pending.lastName() + " " + pending.firstName())
                .phone(pending.phone())
                .addressLine(pending.address())
                .isDefault(true)
                .build());

        return new AccountAndCustomer(account, customer);
    }

    private AuthResponse issueTokenPair(Account account, Role role, UserSummaryResponse userSummary) {
        String accessToken = tokenProvider.generateAccessToken(
                account.getId(), account.getEmail(), role.getName(), account.getStatus().name());
        String refreshToken = tokenProvider.generateRefreshToken(account.getId(), account.getEmail());

        return new AuthResponse(
                accessToken, refreshToken, jwtProperties.getAccessTokenExpirationMs() / MILLIS_IN_SECOND, userSummary);
    }

    private RegisterRequest normalize(RegisterRequest request) {
        return new RegisterRequest(
                request.email().trim().toLowerCase(Locale.ROOT),
                request.phone().trim(),
                request.password(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.gender(),
                request.birthday(),
                request.address().trim());
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
        ensureAccountUsable(account);
        Role role = roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        return issueTokenPair(account, role, buildUserSummary(account, role));
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
                .ifPresent(account -> otpService.generateAndSendOtp(normalized, OTP_PURPOSE_PASSWORD_RESET));
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
