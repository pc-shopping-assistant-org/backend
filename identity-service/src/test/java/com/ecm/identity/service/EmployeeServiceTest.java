package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.dto.request.CreateEmployeeRequest;
import com.ecm.identity.dto.request.UpdateEmployeeRequest;
import com.ecm.identity.dto.response.EmployeeResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Employee;
import com.ecm.identity.entity.Gender;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.EmployeeMapperImpl;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final UUID AVATAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
    private static final long REFRESH_TTL_MS = 60_000L;

    @Mock private AccountRepository accountRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AvatarResolver avatarResolver;
    @Mock private TokenRevocationService tokenRevocationService;

    private EmployeeService service;
    private Account account;
    private Employee employee;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpirationMs(REFRESH_TTL_MS);
        service = new EmployeeService(accountRepository, employeeRepository, roleRepository, passwordEncoder,
                new EmployeeMapperImpl(), avatarResolver,
                new AccountLocker(accountRepository, tokenRevocationService, jwtProperties));
        account = Account.builder().id(ACCOUNT_ID).email("old@shop.vn").phone("0911111111").roleId(ROLE_ID)
                .status(AccountStatus.ACTIVE).build();
        employee = Employee.builder().accountId(ACCOUNT_ID).firstName("An").lastName("Nguyen").gender(Gender.MALE).build();
    }

    private EmployeeResponse response(AccountStatus status, UUID avatarFileId) {
        return new EmployeeResponse(ACCOUNT_ID, account.getEmail(), account.getPhone(), status, "An", "Nguyen",
                Gender.MALE, null, null, avatarFileId, Instant.now());
    }

    private CreateEmployeeRequest createRequest() {
        return new CreateEmployeeRequest("  New@Shop.vn ", " 0922222222 ", "Passw0rdX", " Binh ", " Tran ",
                Gender.FEMALE, null, "1 Street", null);
    }

    @Test
    void createStoresATrimmedEmployeeAccountWithTheFixedRole() {
        when(roleRepository.findByName("ROLE_EMPLOYEE")).thenReturn(Optional.of(Role.builder().id(ROLE_ID).name("ROLE_EMPLOYEE").build()));
        when(passwordEncoder.encode("Passw0rdX")).thenReturn("hash");
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(call -> {
            Account saved = call.getArgument(0);
            saved.setId(ACCOUNT_ID);
            return saved;
        });
        when(employeeRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.of(response(AccountStatus.ACTIVE, null)));

        service.create(createRequest());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("new@shop.vn");
        assertThat(accountCaptor.getValue().getPhone()).isEqualTo("0922222222");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(accountCaptor.getValue().getRoleId()).isEqualTo(ROLE_ID);
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(employeeCaptor.getValue().getFirstName()).isEqualTo("Binh");
        assertThat(employeeCaptor.getValue().getLastName()).isEqualTo("Tran");
    }

    @Test
    void createRejectsADuplicateEmail() {
        when(accountRepository.existsByEmailIgnoreCase("new@shop.vn")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsADuplicatePhone() {
        when(accountRepository.existsByPhone("0922222222")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnUnknownAvatarBeforeWriting() {
        org.mockito.Mockito.doThrow(new BusinessException(IdentityErrorCode.INVALID_AVATAR_FILE))
                .when(avatarResolver).requireExisting(AVATAR_ID);
        CreateEmployeeRequest request = new CreateEmployeeRequest("a@shop.vn", "0933333333", "Passw0rdX", "A", "B",
                Gender.MALE, null, null, AVATAR_ID);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void searchRejectsInvalidPaging() {
        assertThatThrownBy(() -> service.search(null, null, -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, 0, 101)).isInstanceOf(BusinessException.class);
    }

    @Test
    void searchPassesFlagsAndResolvesAllAvatarsInOneCall() {
        when(employeeRepository.search(eq(true), eq(AccountStatus.LOCKED), eq("an"), any()))
                .thenReturn(new PageImpl<>(List.of(response(AccountStatus.LOCKED, AVATAR_ID), response(AccountStatus.LOCKED, null))));
        when(avatarResolver.resolveUrls(any())).thenReturn(Map.of(AVATAR_ID, "http://img/a.png"));

        PageResponse<EmployeeResponse> page = service.search("  an ", AccountStatus.LOCKED, 0, 20);

        assertThat(page.getContent()).extracting(EmployeeResponse::avatarUrl).containsExactly("http://img/a.png", null);
        verify(avatarResolver).resolveUrls(any());
    }

    @Test
    void searchWithoutFiltersUsesTheMatchEverythingDefaults() {
        when(employeeRepository.search(eq(false), any(AccountStatus.class), eq(""), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.search(null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void getByIdReportsNotFound() {
        when(employeeRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(ACCOUNT_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenExistingEmployee() {
        when(employeeRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(employee));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(employeeRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.of(response(AccountStatus.ACTIVE, null)));
    }

    @Test
    void updateChangesOnlyTheFieldsSent() {
        givenExistingEmployee();

        service.update(ACCOUNT_ID, new UpdateEmployeeRequest(null, "0944444444", null, " Hoa ", null, null, "New address", null));

        assertThat(account.getEmail()).isEqualTo("old@shop.vn");
        assertThat(account.getPhone()).isEqualTo("0944444444");
        assertThat(employee.getFirstName()).isEqualTo("An");
        assertThat(employee.getLastName()).isEqualTo("Hoa");
        assertThat(employee.getGender()).isEqualTo(Gender.MALE);
        assertThat(employee.getAddress()).isEqualTo("New address");
        verify(avatarResolver, never()).requireExisting(any());
    }

    @Test
    void updateRejectsAnEmailOfAnotherAccount() {
        when(employeeRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(employee));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCaseAndIdNot("taken@shop.vn", ACCOUNT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ACCOUNT_ID,
                new UpdateEmployeeRequest("Taken@shop.vn", null, null, null, null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(account.getEmail()).isEqualTo("old@shop.vn");
    }

    @Test
    void updateChecksAChangedAvatarButNotTheSameOne() {
        employee.setAvatarFileId(AVATAR_ID);
        givenExistingEmployee();
        UUID other = UUID.randomUUID();

        service.update(ACCOUNT_ID, new UpdateEmployeeRequest(null, null, null, null, null, null, null, AVATAR_ID));
        verify(avatarResolver, never()).requireExisting(AVATAR_ID);

        service.update(ACCOUNT_ID, new UpdateEmployeeRequest(null, null, null, null, null, null, null, other));
        verify(avatarResolver).requireExisting(other);
        assertThat(employee.getAvatarFileId()).isEqualTo(other);
    }

    @Test
    void updateReportsNotFoundForANonEmployee() {
        when(employeeRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(ACCOUNT_ID, new UpdateEmployeeRequest(null, null, null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lockMarksTheAccountLockedAndRevokesItsTokens() {
        when(employeeRepository.existsById(ACCOUNT_ID)).thenReturn(true);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(employeeRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.of(response(AccountStatus.LOCKED, null)));

        EmployeeResponse locked = service.lock(ACCOUNT_ID);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.status()).isEqualTo(AccountStatus.LOCKED);
        verify(tokenRevocationService).revokeBefore(eq(ACCOUNT_ID), any(Instant.class), eq(Duration.ofMillis(REFRESH_TTL_MS)));
    }

    @Test
    void lockRefusesAnAccountThatIsNotAnEmployee() {
        when(employeeRepository.existsById(ACCOUNT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.lock(ACCOUNT_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }

    @Test
    void lockRefusesAnAlreadyLockedAccount() {
        account.setStatus(AccountStatus.LOCKED);
        when(employeeRepository.existsById(ACCOUNT_ID)).thenReturn(true);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.lock(ACCOUNT_ID)).isInstanceOf(InvalidStateException.class);
        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }
}
