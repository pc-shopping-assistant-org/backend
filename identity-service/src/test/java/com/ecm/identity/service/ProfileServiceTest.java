package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.dto.request.UpdateProfileRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CustomerMapper customerMapper;
    @InjectMocks private ProfileService profileService;

    private Account account;
    private Customer customer;

    @BeforeEach
    void setUp() {
        account = Account.builder().id(ACCOUNT_ID).email("u@example.com").phone("0912345678")
                .roleId(ROLE_ID).status(AccountStatus.ACTIVE).build();
        customer = Customer.builder().accountId(ACCOUNT_ID).firstName("An").lastName("Test").build();
        org.mockito.Mockito.lenient().when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
    }

    @Test
    void updateProfileTrimsNamesAndAppliesChangedPhone() {
        Role role = Role.builder().id(ROLE_ID).name("ROLE_CUSTOMER").build();
        UserSummaryResponse summary = mock(UserSummaryResponse.class);
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByPhone("0987654321")).thenReturn(false);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(customerMapper.toSummary(account, role, customer)).thenReturn(summary);

        UserSummaryResponse result = profileService.updateProfile(ACCOUNT_ID,
                new UpdateProfileRequest(" Binh ", " Nguyen ", null, null, "0987654321"));

        assertSame(summary, result);
        assertEquals("Binh", customer.getFirstName());
        assertEquals("Nguyen", customer.getLastName());
        assertEquals("0987654321", account.getPhone());
        verify(accountRepository).save(account);
        verify(customerRepository).save(customer);
    }

    @Test
    void updateProfileRejectsPhoneUsedByAnotherAccount() {
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByPhone("0987654321")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> profileService.updateProfile(ACCOUNT_ID,
                new UpdateProfileRequest("An", "Test", null, null, "0987654321")));

        assertEquals(IdentityErrorCode.PHONE_ALREADY_IN_USE, ex.getErrorCode());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsNonCustomerAccount() {
        when(customerRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> profileService.updateProfile(ACCOUNT_ID,
                new UpdateProfileRequest("An", "Test", null, null, null)));

        assertEquals(IdentityErrorCode.CUSTOMER_PROFILE_REQUIRED, ex.getErrorCode());
    }

    @Test
    void getProfileRejectsInactiveAccount() {
        account.setStatus(AccountStatus.LOCKED);

        BusinessException ex = assertThrows(BusinessException.class, () -> profileService.getProfile(ACCOUNT_ID));

        assertEquals(IdentityErrorCode.ACCOUNT_INACTIVE, ex.getErrorCode());
    }

    @Test
    void customersAreFoundByATrimmedNameAndTheListIsBounded() {
        java.util.UUID found = java.util.UUID.randomUUID();
        when(customerRepository.findAccountIdsByName(org.mockito.ArgumentMatchers.eq("an"), any())).thenReturn(java.util.List.of(found));

        assertEquals(java.util.List.of(found), profileService.findCustomerIdsByName("  an "));

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(customerRepository).findAccountIdsByName(org.mockito.ArgumentMatchers.eq("an"), pageable.capture());
        assertEquals(200, pageable.getValue().getPageSize());
    }

    @Test
    void aBlankNameFindsNobodyWithoutAQuery() {
        assertEquals(java.util.List.of(), profileService.findCustomerIdsByName("   "));
        assertEquals(java.util.List.of(), profileService.findCustomerIdsByName(null));

        verify(customerRepository, never()).findAccountIdsByName(any(), any());
    }

    @Test
    void returnsOnlyTheNamesOfTheRequestedCustomers() {
        when(customerRepository.findAllById(java.util.List.of(ACCOUNT_ID))).thenReturn(java.util.List.of(customer));

        assertEquals(java.util.Map.of(ACCOUNT_ID, "An Test"), profileService.getCustomerNames(java.util.List.of(ACCOUNT_ID)));
    }

    @Test
    void refusesANameLookupLargerThanOnePage() {
        java.util.List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID).limit(51).toList();

        assertThrows(BusinessException.class, () -> profileService.getCustomerNames(tooMany));
        verify(customerRepository, never()).findAllById(any());
    }
}
