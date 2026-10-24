package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.request.GoogleLoginRequest;
import com.ecm.identity.dto.request.LoginRequest;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import com.ecm.identity.event.GoogleAvatarRequested;
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
import com.ecm.identity.service.google.GoogleIdentity;
import com.ecm.identity.service.google.GoogleIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleSignupTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final String EMAIL = "an@example.com";
    private static final String SUBJECT = "google-sub-1";
    private static final String PICTURE = "https://lh3.googleusercontent.com/a/pic";

    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private OtpService otpService;
    @Mock private CustomerMapper customerMapper;
    @Mock private GoogleIdentityVerifier googleIdentityVerifier;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    private AuthService authService;

    private final Role customerRole = Role.builder().id(ROLE_ID).name("ROLE_CUSTOMER").build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        UserSummaryResolver summaries = new UserSummaryResolver(customerRepository, mock(EmployeeRepository.class),
                mock(AdminRepository.class), customerMapper, mock(EmployeeMapper.class), mock(AdminMapper.class));
        TokenIssuer tokenIssuer = new TokenIssuer(tokenProvider, jwtProperties, roleRepository, summaries);
        CustomerRegistrar registrar = new CustomerRegistrar(
                accountRepository, customerRepository, mock(CustomerAddressRepository.class), customerMapper, passwordEncoder);
        GoogleAccountLinker linker = new GoogleAccountLinker(accountRepository, customerRepository, roleRepository, transactionTemplate, eventPublisher);
        authService = new AuthService(accountRepository, roleRepository, passwordEncoder, tokenProvider, jwtProperties, otpService,
                googleIdentityVerifier, customerMapper, mock(TokenRevocationService.class), tokenIssuer, registrar, linker);

        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customerRole));
        when(transactionTemplate.execute(any())).thenAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> {
            Account saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(ACCOUNT_ID);
            }
            return saved;
        });
        when(customerRepository.save(any(Customer.class))).thenAnswer(call -> call.getArgument(0));
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(Customer.builder().accountId(ACCOUNT_ID).build()));
        when(customerMapper.toSummary(any(), any(), any())).thenReturn(mock(UserSummaryResponse.class));
        when(tokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access");
        when(tokenProvider.generateRefreshToken(any(), any())).thenReturn("refresh");
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(1000L);
    }

    private void googleReturns(String given, String family, String full, String picture) {
        when(googleIdentityVerifier.verify("token")).thenReturn(new GoogleIdentity(SUBJECT, EMAIL, given, family, full, picture));
    }

    private AuthResponse signIn() {
        return authService.loginWithGoogle(new GoogleLoginRequest("token"));
    }

    private Account savedAccount() {
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    private Customer savedCustomer() {
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsAnActiveCustomerWithoutPasswordOrPhoneWhenNobodyHasThatEmail() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);

        AuthResponse response = signIn();

        Account account = savedAccount();
        assertEquals(EMAIL, account.getEmail());
        assertEquals(SUBJECT, account.getGoogleSubject());
        assertEquals(ROLE_ID, account.getRoleId());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNull(account.getPasswordHash());
        assertNull(account.getPhone());
        Customer customer = savedCustomer();
        assertEquals("An", customer.getFirstName());
        assertEquals("Nguyen", customer.getLastName());
        assertEquals("access", response.accessToken());
    }

    @Test
    void asksForTheGooglePictureOnlyAfterSavingTheSignup() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);

        signIn();

        verify(eventPublisher).publishEvent(new GoogleAvatarRequested(ACCOUNT_ID, EMAIL, PICTURE));
    }

    @Test
    void asksForNoPictureWhenGoogleHasNone() {
        googleReturns("An", "Nguyen", "An Nguyen", null);

        signIn();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void neverGivesAGoogleSignupAnyRoleButCustomer() {
        googleReturns("An", "Nguyen", "An Nguyen", null);

        signIn();

        assertEquals(ROLE_ID, savedAccount().getRoleId());
        verify(roleRepository).findByName("ROLE_CUSTOMER");
        verify(roleRepository, never()).findByName("ROLE_ADMIN");
    }

    @Test
    void takesTheNameFromTheFullNameWhenGoogleSendsNoSplitName() {
        googleReturns(null, null, "Nguyen Van An", null);

        signIn();

        Customer customer = savedCustomer();
        assertEquals("An", customer.getFirstName());
        assertEquals("Nguyen Van", customer.getLastName());
    }

    @Test
    void takesTheNameFromTheEmailWhenGoogleSendsNoName() {
        googleReturns(null, null, null, null);

        signIn();

        Customer customer = savedCustomer();
        assertEquals("an", customer.getFirstName());
        assertEquals("", customer.getLastName());
    }

    @Test
    void linksTheExistingAccountWithTheSameEmailInsteadOfCreatingAnother() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);
        Account existing = Account.builder().id(ACCOUNT_ID).email(EMAIL).phone("0912345678").passwordHash("hash")
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        signIn();

        assertEquals(SUBJECT, existing.getGoogleSubject());
        verify(customerRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void signsInTheAlreadyLinkedAccountWithoutCreatingAnything() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);
        Account linked = Account.builder().id(ACCOUNT_ID).email(EMAIL).googleSubject(SUBJECT)
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.of(linked));

        signIn();

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void refusesALockedAccountThatAlreadyHasTheEmail() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);
        Account locked = Account.builder().id(ACCOUNT_ID).email(EMAIL).roleId(ROLE_ID).status(AccountStatus.LOCKED).build();
        when(accountRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(locked));

        BusinessException ex = assertThrows(BusinessException.class, this::signIn);

        assertEquals(IdentityErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    }

    @Test
    void usesTheAccountAnotherRequestJustCreatedWhenTheSignupRacesAndLoses() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);
        Account winner = Account.builder().id(ACCOUNT_ID).email(EMAIL).googleSubject(SUBJECT)
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        doThrow(new DataIntegrityViolationException("duplicate")).when(transactionTemplate).execute(any());
        when(accountRepository.findByGoogleSubject(SUBJECT)).thenReturn(Optional.empty(), Optional.of(winner));

        AuthResponse response = signIn();

        assertEquals("access", response.accessToken());
    }

    @Test
    void failsWithoutCreatingAnythingWhenTheCustomerRoleIsMissing() {
        googleReturns("An", "Nguyen", "An Nguyen", PICTURE);
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, this::signIn);

        assertEquals(IdentityErrorCode.ROLE_NOT_CONFIGURED, ex.getErrorCode());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void anAccountWithoutPasswordCannotSignInWithAPassword() {
        Account googleOnly = Account.builder().id(ACCOUNT_ID).email(EMAIL).googleSubject(SUBJECT)
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(googleOnly));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(EMAIL, "anything")));

        assertEquals(IdentityErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void anAccountWithoutPasswordCanSetOneWithJustTheEmailedCode() {
        Account googleOnly = Account.builder().id(ACCOUNT_ID).email(EMAIL).googleSubject(SUBJECT)
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(googleOnly));

        authService.changePassword(ACCOUNT_ID, null, "NewPassw0rd!");

        verify(otpService).savePendingPasswordChange(ACCOUNT_ID, "NewPassw0rd!");
        verify(otpService).generateAndSendOtp(EMAIL, "PASSWORD_CHANGE");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void anAccountWithAPasswordStillHasToGiveTheCurrentOne() {
        Account withPassword = Account.builder().id(ACCOUNT_ID).email(EMAIL).passwordHash("hash")
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(withPassword));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(ACCOUNT_ID, null, "NewPassw0rd!"));

        assertEquals(IdentityErrorCode.INCORRECT_PASSWORD, ex.getErrorCode());
        verify(otpService, never()).generateAndSendOtp(any(), any());
    }
}
