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
class PasswordServiceTest {

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
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        UserSummaryResolver summaries = new UserSummaryResolver(customerRepository, mock(EmployeeRepository.class),
                mock(AdminRepository.class), customerMapper, mock(EmployeeMapper.class), mock(AdminMapper.class));
        TokenIssuer tokenIssuer = new TokenIssuer(tokenProvider, jwtProperties, roleRepository, summaries);
        CustomerRegistrar registrar = new CustomerRegistrar(
                accountRepository, customerRepository, mock(CustomerAddressRepository.class), customerMapper, passwordEncoder);
        GoogleAccountLinker linker = new GoogleAccountLinker(accountRepository, customerRepository, roleRepository, mock(TransactionTemplate.class), mock(ApplicationEventPublisher.class));
        passwordService = new PasswordService(accountRepository, passwordEncoder, otpService);
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest(" User@Example.com ", "0912345678", "Passw0rd!x", "An", "Test", null, null, "1 Test St");
    }

    private static Account account(AccountStatus status) {
        return Account.builder().id(ACCOUNT_ID).email(EMAIL).phone("0912345678")
                .passwordHash("hash").roleId(ROLE_ID).status(status).build();
    }

    @Test
    void resetPasswordUpdatesHashWhenOtpMatches() {
        Account account = account(AccountStatus.ACTIVE);
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_RESET", "123456")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1!")).thenReturn("newHash");

        passwordService.resetPassword(" " + EMAIL, "123456", "NewPass1!");

        assertEquals("newHash", account.getPasswordHash());
        verify(accountRepository).save(account);
    }

    @Test
    void resetPasswordRejectsWrongOtp() {
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_RESET", "bad")).thenReturn(false);

        assertThrows(BusinessException.class, () -> passwordService.resetPassword(EMAIL, "bad", "NewPass1!"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void forgotPasswordSendsOtpOnlyForActiveAccount() {
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.LOCKED)));
        passwordService.forgotPassword(EMAIL);
        verify(otpService, never()).generateAndSendOtp(any(), any());

        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        passwordService.forgotPassword(EMAIL);
        verify(otpService).generateAndSendOtp(EMAIL, "PASSWORD_RESET");
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> passwordService.changePassword(ACCOUNT_ID, "bad", "NewPass1!"));

        assertEquals(IdentityErrorCode.INCORRECT_PASSWORD, ex.getErrorCode());
        verify(otpService, never()).savePendingPasswordChange(any(), any());
    }

    @Test
    void changePasswordCachesNewPasswordAndSendsOtp() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);

        passwordService.changePassword(ACCOUNT_ID, "old", "NewPass1!");

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

        passwordService.verifyPasswordChangeOtp(ACCOUNT_ID, "123456");

        assertEquals("newHash", account.getPasswordHash());
        verify(otpService).deletePendingPasswordChange(ACCOUNT_ID);
    }

    @Test
    void verifyPasswordChangeOtpFailsWhenPendingPasswordExpired() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE)));
        when(otpService.verifyAndConsumeOtp(EMAIL, "PASSWORD_CHANGE", "123456")).thenReturn(true);
        when(otpService.getPendingPasswordChange(ACCOUNT_ID)).thenReturn(null);

        assertThrows(ExpiredException.class, () -> passwordService.verifyPasswordChangeOtp(ACCOUNT_ID, "123456"));
    }
}
