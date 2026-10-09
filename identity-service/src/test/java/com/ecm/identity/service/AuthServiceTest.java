package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ExpiredException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.request.LoginRequest;
import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.request.VerifyOtpRequest;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final String EMAIL = "user@example.com";
    private static final long REFRESH_TTL_MS = 604_800_000L;

    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private OtpService otpService;
    @Mock private CustomerMapper customerMapper;
    @Mock private TokenRevocationService tokenRevocationService;
    @InjectMocks private AuthService authService;

    private static RegisterRequest registerRequest() {
        return new RegisterRequest(" User@Example.com ", "0912345678", "Passw0rd!x", "An", "Test", null, null, "1 Test St");
    }

    private static Account account(AccountStatus status) {
        return Account.builder().id(ACCOUNT_ID).email(EMAIL).phone("0912345678")
                .passwordHash("hash").roleId(ROLE_ID).status(status).build();
    }

    // ---- UC-AUTH-002 register ----

    @Test
    void registerCachesNormalizedPayloadAndSendsOtp() {
        authService.register(registerRequest());

        ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(otpService).savePendingRegistration(eq(EMAIL), captor.capture());
        assertEquals(EMAIL, captor.getValue().email());
        verify(otpService).generateAndSendOtp(EMAIL, "REGISTRATION");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(accountRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest()));
        verify(otpService, never()).generateAndSendOtp(any(), any());
    }

    @Test
    void registerRejectsDuplicatePhone() {
        when(accountRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest()));
        verify(otpService, never()).savePendingRegistration(any(), any());
    }

    @Test
    void verifyRegistrationOtpRejectsWrongOtp() {
        when(otpService.verifyOtp(EMAIL, "REGISTRATION", "000000")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.verifyRegistrationOtp(new VerifyOtpRequest(EMAIL, "000000")));
        assertEquals(IdentityErrorCode.INVALID_OTP, ex.getErrorCode());
    }

    @Test
    void verifyRegistrationOtpFailsWhenPendingRegistrationExpired() {
        when(otpService.verifyOtp(EMAIL, "REGISTRATION", "123456")).thenReturn(true);
        when(otpService.getPendingRegistration(EMAIL)).thenReturn(null);

        assertThrows(ExpiredException.class,
                () -> authService.verifyRegistrationOtp(new VerifyOtpRequest(EMAIL, "123456")));
    }

    // ---- UC-AUTH-001 login ----

    @Test
    void loginIssuesTokenPairForValidCredentials() {
        Account account = account(AccountStatus.ACTIVE);
        Role role = Role.builder().id(ROLE_ID).name("ROLE_CUSTOMER").build();
        Customer customer = Customer.builder().accountId(ACCOUNT_ID).build();
        UserSummaryResponse summary = mock(UserSummaryResponse.class);
        when(accountRepository.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(customer));
        when(customerMapper.toSummary(account, role, customer)).thenReturn(summary);
        when(tokenProvider.generateAccessToken(ACCOUNT_ID, EMAIL, "ROLE_CUSTOMER", "ACTIVE")).thenReturn("access");
        when(tokenProvider.generateRefreshToken(ACCOUNT_ID, EMAIL)).thenReturn("refresh");
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(86_400_000L);

        AuthResponse response = authService.login(new LoginRequest(" " + EMAIL + " ", "pw"));

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertSame(summary, response.user());
    }

    @Test
    void loginDoesNotRevealWhetherIdentifierExists() {
        when(accountRepository.findByLoginIdentifier(EMAIL)).thenReturn(Optional.empty());
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(EMAIL, "pw")));

        when(accountRepository.findByLoginIdentifier("other@example.com"))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);
        BusinessException wrongPassword = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("other@example.com", "bad")));

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, unknown.getErrorCode());
        assertEquals(unknown.getErrorCode(), wrongPassword.getErrorCode());
    }

    @Test
    void loginRejectsLockedAccountBeforeCheckingPassword() {
        when(accountRepository.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(account(AccountStatus.LOCKED)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(EMAIL, "pw")));

        assertEquals(IdentityErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---- UC-AUTH-004 logout ----

    @Test
    void logoutRevokesEveryTokenIssuedBeforeNowForRefreshLifetime() {
        when(tokenProvider.validateToken("tok")).thenReturn(true);
        when(tokenProvider.isRefreshToken("tok")).thenReturn(false);
        when(tokenProvider.getAccountId("tok")).thenReturn(ACCOUNT_ID);
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(REFRESH_TTL_MS);
        Instant before = Instant.now();

        assertTrue(authService.logout("tok"));

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tokenRevocationService).revokeBefore(eq(ACCOUNT_ID), cutoff.capture(), eq(Duration.ofMillis(REFRESH_TTL_MS)));
        assertTrue(cutoff.getValue().isAfter(before), "cutoff must be the logout time, not the token iat");
    }

    @Test
    void logoutRejectsInvalidOrRefreshToken() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        assertThrows(BusinessException.class, () -> authService.logout("bad"));

        when(tokenProvider.validateToken("refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh")).thenReturn(true);
        assertThrows(BusinessException.class, () -> authService.logout("refresh"));

        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }

    // ---- refresh token ----

    private void refreshTokenIssuedAt(Instant issuedAt) {
        when(tokenProvider.validateToken("refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh")).thenReturn(true);
        when(tokenProvider.getAccountId("refresh")).thenReturn(ACCOUNT_ID);
        when(tokenProvider.getIssuedAt("refresh")).thenReturn(issuedAt);
    }

    @Test
    void refreshIssuesANewTokenPairForAValidRefreshToken() {
        Account account = account(AccountStatus.ACTIVE);
        Role role = Role.builder().id(ROLE_ID).name("ROLE_CUSTOMER").build();
        Customer customer = Customer.builder().accountId(ACCOUNT_ID).build();
        UserSummaryResponse summary = mock(UserSummaryResponse.class);
        refreshTokenIssuedAt(Instant.now());
        when(tokenRevocationService.isRevoked(eq(ACCOUNT_ID), any())).thenReturn(false);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(customer));
        when(customerMapper.toSummary(account, role, customer)).thenReturn(summary);
        when(tokenProvider.generateAccessToken(ACCOUNT_ID, EMAIL, "ROLE_CUSTOMER", "ACTIVE")).thenReturn("access2");
        when(tokenProvider.generateRefreshToken(ACCOUNT_ID, EMAIL)).thenReturn("refresh2");
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(86_400_000L);

        AuthResponse response = authService.refresh("refresh");

        assertEquals("access2", response.accessToken());
        assertEquals("refresh2", response.refreshToken());
    }

    @Test
    void refreshRejectsAnInvalidTokenOrAnAccessToken() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        when(tokenProvider.validateToken("access")).thenReturn(true);
        when(tokenProvider.isRefreshToken("access")).thenReturn(false);

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> authService.refresh("bad")).getErrorCode());
        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> authService.refresh("access")).getErrorCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void refreshRejectsATokenIssuedBeforeALogout() {
        refreshTokenIssuedAt(Instant.now().minusSeconds(60));
        when(tokenRevocationService.isRevoked(eq(ACCOUNT_ID), any())).thenReturn(true);

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> authService.refresh("refresh")).getErrorCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void refreshRejectsALockedAccount() {
        refreshTokenIssuedAt(Instant.now());
        when(tokenRevocationService.isRevoked(eq(ACCOUNT_ID), any())).thenReturn(false);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.LOCKED)));

        assertEquals(IdentityErrorCode.ACCOUNT_LOCKED,
                assertThrows(BusinessException.class, () -> authService.refresh("refresh")).getErrorCode());
    }

    // ---- UC-AUTH-003 reset password ----

    @Test
    void resetPasswordUpdatesHashWhenOtpMatches() {
        Account account = account(AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_RESET", "123456")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("newHash");

        authService.resetPassword(" " + EMAIL, "123456", "NewPass1!");

        assertEquals("newHash", account.getPasswordHash());
        verify(accountRepository).save(account);
    }

    @Test
    void resetPasswordRejectsWrongOtp() {
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_RESET", "bad")).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.resetPassword(EMAIL, "bad", "NewPass1!"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void forgotPasswordSendsOtpOnlyForActiveAccount() {
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.LOCKED)));
        authService.forgotPassword(EMAIL);
        verify(otpService, never()).generateAndSendOtp(any(), any());

        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        authService.forgotPassword(EMAIL);
        verify(otpService).generateAndSendOtp(EMAIL, "PASSWORD_RESET");
    }

    // ---- UC-AUTH-005 change password ----

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(ACCOUNT_ID, "bad", "NewPass1!"));

        assertEquals(IdentityErrorCode.INCORRECT_PASSWORD, ex.getErrorCode());
        verify(otpService, never()).savePendingPasswordChange(any(), any());
    }

    @Test
    void changePasswordCachesNewPasswordAndSendsOtp() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);

        authService.changePassword(ACCOUNT_ID, "old", "NewPass1!");

        verify(otpService).savePendingPasswordChange(ACCOUNT_ID, "NewPass1!");
        verify(otpService).generateAndSendOtp(EMAIL, "PASSWORD_CHANGE");
    }

    @Test
    void verifyPasswordChangeOtpAppliesPendingPassword() {
        Account account = account(AccountStatus.ACTIVE);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_CHANGE", "123456")).thenReturn(true);
        when(otpService.getPendingPasswordChange(ACCOUNT_ID)).thenReturn("NewPass1!");
        when(passwordEncoder.encode("NewPass1!")).thenReturn("newHash");

        authService.verifyPasswordChangeOtp(ACCOUNT_ID, "123456");

        assertEquals("newHash", account.getPasswordHash());
        verify(otpService).deletePendingPasswordChange(ACCOUNT_ID);
    }

    @Test
    void verifyPasswordChangeOtpFailsWhenPendingPasswordExpired() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_CHANGE", "123456")).thenReturn(true);
        when(otpService.getPendingPasswordChange(ACCOUNT_ID)).thenReturn(null);

        assertThrows(ExpiredException.class, () -> authService.verifyPasswordChangeOtp(ACCOUNT_ID, "123456"));
    }
}
