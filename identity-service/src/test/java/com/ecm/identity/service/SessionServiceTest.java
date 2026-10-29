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
class SessionServiceTest {

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
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        UserSummaryResolver summaries = new UserSummaryResolver(customerRepository, mock(EmployeeRepository.class),
                mock(AdminRepository.class), customerMapper, mock(EmployeeMapper.class), mock(AdminMapper.class));
        TokenIssuer tokenIssuer = new TokenIssuer(tokenProvider, jwtProperties, roleRepository, summaries);
        CustomerRegistrar registrar = new CustomerRegistrar(
                accountRepository, customerRepository, mock(CustomerAddressRepository.class), customerMapper, passwordEncoder);
        GoogleAccountLinker linker = new GoogleAccountLinker(accountRepository, customerRepository, roleRepository, mock(TransactionTemplate.class), mock(ApplicationEventPublisher.class));
        sessionService = new SessionService(accountRepository, tokenProvider, jwtProperties, tokenRevocationService, tokenIssuer);
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest(" User@Example.com ", "0912345678", "Passw0rd!x", "An", "Test", null, null, "1 Test St");
    }

    private static Account account(AccountStatus status) {
        return Account.builder().id(ACCOUNT_ID).email(EMAIL).phone("0912345678")
                .passwordHash("hash").roleId(ROLE_ID).status(status).build();
    }

    @Test
    void logoutRevokesEveryTokenIssuedBeforeNowForRefreshLifetime() {
        when(tokenProvider.validateToken("tok")).thenReturn(true);
        when(tokenProvider.isRefreshToken("tok")).thenReturn(false);
        when(tokenProvider.getAccountId("tok")).thenReturn(ACCOUNT_ID);
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(REFRESH_TTL_MS);
        Instant before = Instant.now();

        assertTrue(sessionService.logout("tok"));

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tokenRevocationService).revokeBefore(eq(ACCOUNT_ID), cutoff.capture(), eq(Duration.ofMillis(REFRESH_TTL_MS)));
        assertTrue(cutoff.getValue().isAfter(before), "cutoff must be the logout time, not the token iat");
    }

    @Test
    void logoutRejectsInvalidOrRefreshToken() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        assertThrows(BusinessException.class, () -> sessionService.logout("bad"));

        when(tokenProvider.validateToken("refresh")).thenReturn(true);
        when(tokenProvider.isRefreshToken("refresh")).thenReturn(true);
        assertThrows(BusinessException.class, () -> sessionService.logout("refresh"));

        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }

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

        AuthResponse response = sessionService.refresh("refresh");

        assertEquals("access2", response.accessToken());
        assertEquals("refresh2", response.refreshToken());
    }

    @Test
    void refreshRejectsAnInvalidTokenOrAnAccessToken() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        when(tokenProvider.validateToken("access")).thenReturn(true);
        when(tokenProvider.isRefreshToken("access")).thenReturn(false);

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> sessionService.refresh("bad")).getErrorCode());
        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> sessionService.refresh("access")).getErrorCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void refreshRejectsATokenIssuedBeforeALogout() {
        refreshTokenIssuedAt(Instant.now().minusSeconds(60));
        when(tokenRevocationService.isRevoked(eq(ACCOUNT_ID), any())).thenReturn(true);

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS,
                assertThrows(BusinessException.class, () -> sessionService.refresh("refresh")).getErrorCode());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void refreshRejectsALockedAccount() {
        refreshTokenIssuedAt(Instant.now());
        when(tokenRevocationService.isRevoked(eq(ACCOUNT_ID), any())).thenReturn(false);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.LOCKED)));

        assertEquals(IdentityErrorCode.ACCOUNT_LOCKED,
                assertThrows(BusinessException.class, () -> sessionService.refresh("refresh")).getErrorCode());
    }
}
