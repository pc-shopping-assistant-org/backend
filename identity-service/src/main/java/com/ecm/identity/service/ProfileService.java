package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.dto.request.UpdateProfileRequest;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.helper.AvatarResolver;
import com.ecm.identity.helper.UserSummaryResolver;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.util.AccountChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {

    /** Enough for another service to filter by, without ever returning an unbounded list. */
    private static final int MAX_CUSTOMER_IDS = 200;

    /** Matches the page size of the public review list, the only caller. */
    private static final int MAX_NAME_LOOKUP_IDS = 50;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final CustomerMapper customerMapper;
    private final AvatarResolver avatarResolver;
    private final UserSummaryResolver userSummaryResolver;

    @Transactional(readOnly = true)
    public UserSummaryResponse getProfile(UUID accountId) {
        // 1. Retrieve account and role
        Account account = findAccount(accountId);
        AccountChecks.ensureUsable(account);
        Role role = findRole(account);

        // 2. Map the existing customer, employee or admin profile
        return withAvatarUrl(userSummaryResolver.resolve(account, role));
    }

    /** The accounts of the customers whose name contains the text, for a service that holds only account ids and has to search by name. */
    @Transactional(readOnly = true)
    public List<UUID> findCustomerIdsByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return customerRepository.findAccountIdsByName(name.trim(), PageRequest.of(0, MAX_CUSTOMER_IDS));
    }

    /** Display names of the given customers, nothing else; ids with no customer are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, String> getCustomerNames(Collection<UUID> accountIds) {
        if (accountIds.size() > MAX_NAME_LOOKUP_IDS) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        return customerRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Customer::getAccountId, customer -> customer.getLastName() + " " + customer.getFirstName()));
    }

    @Transactional
    public UserSummaryResponse updateProfile(UUID accountId, UpdateProfileRequest request) {
        // 1. Retrieve account and customer profile
        Account account = findAccount(accountId);
        AccountChecks.ensureUsable(account);
        Customer customer = customerRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.CUSTOMER_PROFILE_REQUIRED));

        // 2. Validate and update phone if changed
        if (request.phone() != null && !request.phone().equals(account.getPhone())) {
            if (accountRepository.existsByPhone(request.phone())) {
                throw new BusinessException(IdentityErrorCode.PHONE_ALREADY_EXISTS);
            }
            account.setPhone(request.phone());
        }

        // 3. The avatar has to be a file the Media Service knows
        avatarResolver.requireExisting(request.avatarFileId());

        // 4. Update customer profile fields
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        if (request.gender() != null) {
            customer.setGender(request.gender());
        }
        if (request.birthday() != null) {
            customer.setBirthday(request.birthday());
        }
        if (request.avatarFileId() != null) {
            customer.setAvatarFileId(request.avatarFileId());
        }
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            // Someone took the number between the check above and this save
            throw new BusinessException(IdentityErrorCode.PHONE_ALREADY_EXISTS);
        }
        customerRepository.save(customer);

        // 5. Map the updated profile
        return withAvatarUrl(customerMapper.toSummary(account, findRole(account), customer));
    }

    private UserSummaryResponse withAvatarUrl(UserSummaryResponse summary) {
        UUID fileId = summary.avatarFileId();
        if (fileId == null) {
            return summary;
        }
        return summary.withAvatarUrl(avatarResolver.resolveUrls(List.of(fileId)).get(fileId));
    }

    private Account findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private Role findRole(Account account) {
        return roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
    }
}
