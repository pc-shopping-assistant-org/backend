package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.InvalidStateException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.dto.response.CustomerDetailResponse;
import com.ecm.identity.dto.response.CustomerResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.CustomerAddress;
import com.ecm.identity.entity.Gender;
import com.ecm.identity.mapper.AddressMapperImpl;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

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
class CustomerServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID AVATAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final long REFRESH_TTL_MS = 60_000L;

    @Mock private AccountRepository accountRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAddressRepository customerAddressRepository;
    @Mock private AvatarResolver avatarResolver;
    @Mock private TokenRevocationService tokenRevocationService;

    private CustomerService service;
    private Account account;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpirationMs(REFRESH_TTL_MS);
        service = new CustomerService(accountRepository, customerRepository, customerAddressRepository,
                new AddressMapperImpl(), avatarResolver,
                new AccountLocker(accountRepository, tokenRevocationService, jwtProperties));
        account = Account.builder().id(ACCOUNT_ID).email("c@shop.vn").phone("0911111111").status(AccountStatus.ACTIVE).build();
    }

    private CustomerResponse response(AccountStatus status, UUID avatarFileId) {
        return new CustomerResponse(ACCOUNT_ID, "c@shop.vn", "0911111111", status, "An", "Nguyen", Gender.OTHER, null,
                avatarFileId, Instant.now());
    }

    @Test
    void searchRejectsInvalidPagingAndAnEmptyDateRange() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> service.search(null, null, null, null, -1, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, null, null, 0, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, null, null, 0, 101)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, now, now, 0, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(null, null, now, now.minusSeconds(1), 0, 20)).isInstanceOf(BusinessException.class);
    }

    @Test
    void searchPassesTheFiltersAndResolvesAllAvatarsInOneCall() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        when(customerRepository.search(eq(true), eq(AccountStatus.LOCKED), eq(from), eq(to), eq("an"), any()))
                .thenReturn(new PageImpl<>(List.of(response(AccountStatus.LOCKED, AVATAR_ID), response(AccountStatus.LOCKED, null))));
        when(avatarResolver.resolveUrls(any())).thenReturn(Map.of(AVATAR_ID, "http://img/c.png"));

        PageResponse<CustomerResponse> page = service.search("  an ", AccountStatus.LOCKED, from, to, 0, 20);

        assertThat(page.getContent()).extracting(CustomerResponse::avatarUrl).containsExactly("http://img/c.png", null);
        verify(avatarResolver).resolveUrls(any());
    }

    @Test
    void searchWithoutFiltersUsesTheMatchEverythingDefaults() {
        when(customerRepository.search(eq(false), any(AccountStatus.class), eq(Instant.EPOCH), any(Instant.class), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.search(null, null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void detailIncludesTheAddressesDefaultFirst() {
        when(customerRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.of(response(AccountStatus.ACTIVE, null)));
        when(customerAddressRepository.findByCustomerIdOrderByIsDefaultDesc(ACCOUNT_ID)).thenReturn(List.of(
                CustomerAddress.builder().id(UUID.randomUUID()).customerId(ACCOUNT_ID).recipientName("An").phone("0911111111")
                        .addressLine("1 Street").isDefault(true).build()));

        CustomerDetailResponse detail = service.getById(ACCOUNT_ID);

        assertThat(detail.email()).isEqualTo("c@shop.vn");
        assertThat(detail.addresses()).extracting(AddressResponse::addressLine).containsExactly("1 Street");
    }

    @Test
    void detailReportsNotFound() {
        when(customerRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(ACCOUNT_ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lockChangesOnlyTheAccountAndRevokesItsTokens() {
        when(customerRepository.existsById(ACCOUNT_ID)).thenReturn(true);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(customerRepository.findDetail(ACCOUNT_ID)).thenReturn(Optional.of(response(AccountStatus.LOCKED, null)));

        CustomerResponse locked = service.lock(ACCOUNT_ID);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.status()).isEqualTo(AccountStatus.LOCKED);
        verify(tokenRevocationService).revokeBefore(eq(ACCOUNT_ID), any(Instant.class), eq(Duration.ofMillis(REFRESH_TTL_MS)));
    }

    @Test
    void lockRefusesAnAccountThatIsNotACustomer() {
        when(customerRepository.existsById(ACCOUNT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.lock(ACCOUNT_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }

    @Test
    void lockRefusesAnAlreadyLockedAccount() {
        account.setStatus(AccountStatus.LOCKED);
        when(customerRepository.existsById(ACCOUNT_ID)).thenReturn(true);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.lock(ACCOUNT_ID)).isInstanceOf(InvalidStateException.class);
        verify(tokenRevocationService, never()).revokeBefore(any(), any(), any());
    }
}
