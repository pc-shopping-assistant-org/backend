package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.dto.response.CustomerDetailResponse;
import com.ecm.identity.dto.response.CustomerResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.mapper.AddressMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer accounts as seen by the shop's staff. Authorization is enforced by SecurityConfig on {@code /customers/**}.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final String RESOURCE_CUSTOMER = "Customer";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final AddressMapper addressMapper;
    private final AvatarResolver avatarResolver;
    private final AccountLocker accountLocker;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String keyword, AccountStatus status, Instant createdFrom,
                                                 Instant createdTo, int page, int size) {
        // 1. Bound the paging and the date range
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One query for the page; every filter is optional
        Page<CustomerResponse> customers = customerRepository.search(
                status != null, status != null ? status : AccountStatus.ACTIVE,
                createdFrom == null ? Instant.EPOCH : createdFrom, createdTo == null ? FAR_FUTURE : createdTo,
                keyword == null ? "" : keyword.trim(), PageRequest.of(page, size));

        // 3. One Media call for the whole page
        Map<UUID, String> urls = avatarResolver.resolveUrls(
                customers.getContent().stream().map(CustomerResponse::avatarFileId).toList());
        return PageResponse.of(customers.map(customer -> withUrl(customer, urls)));
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse getById(UUID accountId) {
        // 1. The customer, with the avatar URL
        CustomerResponse customer = detail(accountId);

        // 2. Their saved addresses, default first
        List<AddressResponse> addresses = customerAddressRepository.findByCustomerIdOrderByIsDefaultDesc(accountId).stream()
                .map(addressMapper::toResponse)
                .toList();
        return CustomerDetailResponse.of(customer, addresses);
    }

    /**
     * Only the account changes: profile, addresses and orders stay as they are, and orders in progress keep going.
     */
    @Transactional
    public CustomerResponse lock(UUID accountId) {
        // 1. Only a customer account can be locked here
        if (!customerRepository.existsById(accountId)) {
            throw new ResourceNotFoundException(RESOURCE_CUSTOMER, accountId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_CUSTOMER, accountId));

        // 2. Lock it and cut off the tokens already issued
        accountLocker.lock(account, RESOURCE_CUSTOMER);

        // 3. Answer with the stored row
        return detail(accountId);
    }

    private CustomerResponse detail(UUID accountId) {
        CustomerResponse customer = customerRepository.findDetail(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_CUSTOMER, accountId));
        return withUrl(customer, avatarResolver.resolveUrls(Collections.singletonList(customer.avatarFileId())));
    }

    private CustomerResponse withUrl(CustomerResponse customer, Map<UUID, String> urls) {
        return customer.avatarFileId() == null ? customer : customer.withAvatarUrl(urls.get(customer.avatarFileId()));
    }
}
