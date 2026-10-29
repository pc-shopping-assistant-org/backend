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
import com.ecm.identity.helper.CustomerRegistrar;
import com.ecm.identity.helper.GoogleAccountLinker;
import com.ecm.identity.helper.TokenIssuer;
import com.ecm.identity.helper.UserSummaryResolver;
import com.ecm.identity.mapper.AdminMapper;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AdminRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.service.google.GoogleIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

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
    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserSummaryResolver summaries = new UserSummaryResolver(customerRepository, mock(EmployeeRepository.class),
                mock(AdminRepository.class), customerMapper, mock(EmployeeMapper.class), mock(AdminMapper.class));
        TokenIssuer tokenIssuer = new TokenIssuer(tokenProvider, jwtProperties, roleRepository, summaries);
        CustomerRegistrar registrar = new CustomerRegistrar(
                accountRepository, customerRepository, mock(CustomerAddressRepository.class), customerMapper, passwordEncoder);
        GoogleAccountLinker linker = new GoogleAccountLinker(accountRepository, customerRepository, roleRepository, mock(TransactionTemplate.class), mock(ApplicationEventPublisher.class));
        authService = new AuthService(accountRepository, roleRepository, passwordEncoder, otpService,
                mock(GoogleIdentityVerifier.class), customerMapper, tokenIssuer, registrar, linker);
    }

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

    // ---- refresh token ----

    // ---- UC-AUTH-003 reset password ----

    // ---- UC-AUTH-005 change password ----
}
