package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.dto.request.UpdateProfileRequest;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.AdminMapper;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.AdminRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
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
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final RoleRepository roleRepository;
    private final CustomerMapper customerMapper;
    private final EmployeeMapper employeeMapper;
    private final AdminMapper adminMapper;

    @Transactional(readOnly = true)
    public UserSummaryResponse getProfile(UUID accountId) {
        // 1. Retrieve account and role
        Account account = findAccount(accountId);
        ensureActive(account);
        Role role = findRole(account);

        // 2. Map the existing customer, employee or admin profile
        return customerRepository.findById(accountId)
                .map(customer -> customerMapper.toSummary(account, role, customer))
                .or(() -> employeeRepository.findById(accountId)
                        .map(employee -> employeeMapper.toSummary(account, role, employee)))
                .or(() -> adminRepository.findById(accountId)
                        .map(admin -> adminMapper.toSummary(account, role, admin)))
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", accountId));
    }

    @Transactional
    public UserSummaryResponse updateProfile(UUID accountId, UpdateProfileRequest request) {
        // 1. Retrieve account and customer profile
        Account account = findAccount(accountId);
        ensureActive(account);
        Customer customer = customerRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.CUSTOMER_PROFILE_REQUIRED));

        // 2. Validate and update phone if changed
        if (request.phone() != null && !request.phone().equals(account.getPhone())) {
            if (accountRepository.existsByPhone(request.phone())) {
                throw new BusinessException(IdentityErrorCode.PHONE_ALREADY_IN_USE);
            }
            account.setPhone(request.phone());
        }

        // 3. Update customer profile fields
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        if (request.gender() != null) {
            customer.setGender(request.gender());
        }
        if (request.birthday() != null) {
            customer.setBirthday(request.birthday());
        }
        accountRepository.save(account);
        customerRepository.save(customer);

        // 4. Map the updated profile
        return customerMapper.toSummary(account, findRole(account), customer);
    }

    private Account findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private void ensureActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private Role findRole(Account account) {
        return roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
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
                .collect(Collectors.toMap(Customer::getAccountId, customer -> customer.getFirstName() + " " + customer.getLastName()));
    }
}
