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
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.service.google.GoogleIdentity;
import com.ecm.identity.service.google.GoogleIdentityVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    private static final String OTP_PURPOSE_REGISTRATION = "REGISTRATION";
    private static final String OTP_PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final int MILLIS_IN_SECOND = 1000;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final OtpService otpService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final CustomerMapper customerMapper;
    private final EmployeeMapper employeeMapper;
    private final TokenBlacklistService tokenBlacklistService;

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
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
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
            // 3. First time: link to existing local account by email
            account = accountRepository.findByEmailIgnoreCase(identity.email())
                    .orElseThrow(() -> new BusinessException(IdentityErrorCode.GOOGLE_ACCOUNT_NOT_LINKED));

            // 4. Prevent re-linking if another Google identity is already bound
            if (StringUtils.hasText(account.getGoogleSubject())
                    && !identity.subject().equals(account.getGoogleSubject())) {
                throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
            }

            // 5. Check account is active before linking
            ensureAccountUsable(account);

            // 6. Persist the link
            account.setGoogleSubject(identity.subject());
            account = accountRepository.save(account);
        } else {
            // Already linked — just validate usability
            ensureAccountUsable(account);
        }

        // 7. Issue token pair
        Role role = roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        return issueTokenPair(account, role, buildUserSummary(account, role));
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
                .recipientName(pending.firstName() + " " + pending.lastName())
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

    public boolean logout(String accessToken) {
        // 1. Reject malformed, expired, or refresh tokens
        if (!tokenProvider.validateToken(accessToken) || tokenProvider.isRefreshToken(accessToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 2. Extract remaining validity period from the access token
        long expirationSeconds = tokenProvider.getExpirationSeconds(accessToken);

        // 3. Attempt server-side revocation without blocking client-side logout on Redis outage
        return tokenBlacklistService.tryBlacklistToken(accessToken, expirationSeconds);
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

    @Transactional
    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        // 1. Retrieve an active account
        Account account = accountRepository.findById(accountId)
                .filter(existing -> existing.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        // 2. Verify the current password
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(IdentityErrorCode.INCORRECT_PASSWORD);
        }

        // 3. Update to the new password
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
    }
}
