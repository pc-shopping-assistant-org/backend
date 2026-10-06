package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.dto.request.CreateEmployeeRequest;
import com.ecm.identity.dto.request.UpdateEmployeeRequest;
import com.ecm.identity.dto.response.EmployeeResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Employee;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.EmployeeRepository;
import com.ecm.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Employee accounts, managed by the admin. Authorization is enforced by SecurityConfig on {@code /employees/**}.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";
    private static final String RESOURCE_EMPLOYEE = "Employee";
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeMapper employeeMapper;
    private final AvatarResolver avatarResolver;
    private final AccountLocker accountLocker;

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        // 1. Identity fields are stored trimmed, the email lower-cased, so lookups stay stable
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone().trim();

        // 2. Reject duplicates and an unknown avatar before writing anything
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", email);
        }
        if (accountRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException(IdentityErrorCode.PHONE_ALREADY_EXISTS, "Account", "phone", phone);
        }
        avatarResolver.requireExisting(request.avatarFileId());

        // 3. The role is fixed by this use case, never chosen by the caller
        Role employeeRole = roleRepository.findByName(ROLE_EMPLOYEE)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));

        // 4. Persist account and profile together; a concurrent duplicate surfaces as a unique violation
        Account account = Account.builder()
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.password()))
                .roleId(employeeRole.getId())
                .status(AccountStatus.ACTIVE)
                .build();
        try {
            account = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", email);
        }
        Employee employee = employeeMapper.toEntity(request);
        employee.setAccountId(account.getId());
        employee.setFirstName(employee.getFirstName().trim());
        employee.setLastName(employee.getLastName().trim());
        employeeRepository.save(employee);

        // 5. Answer with the stored row
        return detail(account.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String keyword, AccountStatus status, int page, int size) {
        // 1. Bound the paging
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        // 2. One query for the page; the status filter and keyword are optional
        Page<EmployeeResponse> employees = employeeRepository.search(
                status != null, status != null ? status : AccountStatus.ACTIVE,
                keyword == null ? "" : keyword.trim(), PageRequest.of(page, size));

        // 3. One Media call for the whole page
        Map<UUID, String> urls = avatarResolver.resolveUrls(
                employees.getContent().stream().map(EmployeeResponse::avatarFileId).toList());
        return PageResponse.of(employees.map(employee -> withUrl(employee, urls)));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(UUID accountId) {
        return detail(accountId);
    }

    @Transactional
    public EmployeeResponse update(UUID accountId, UpdateEmployeeRequest request) {
        // 1. Load the account and its employee profile
        Employee employee = employeeRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_EMPLOYEE, accountId));
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_EMPLOYEE, accountId));

        // 2. Email and phone must stay unique across every other account
        applyContact(account, request);

        // 3. A new avatar has to exist in the Media Service
        if (request.avatarFileId() != null && !request.avatarFileId().equals(employee.getAvatarFileId())) {
            avatarResolver.requireExisting(request.avatarFileId());
        }

        // 4. Fields left out keep their value
        employeeMapper.updateEntity(employee, request);
        trimNames(employee);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", account.getEmail());
        }
        employeeRepository.save(employee);

        // 5. Answer with the stored row
        return detail(accountId);
    }

    @Transactional
    public EmployeeResponse lock(UUID accountId) {
        // 1. Only an employee account can be locked here
        if (!employeeRepository.existsById(accountId)) {
            throw new ResourceNotFoundException(RESOURCE_EMPLOYEE, accountId);
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_EMPLOYEE, accountId));

        // 2. Lock it and cut off the tokens already issued
        accountLocker.lock(account, RESOURCE_EMPLOYEE);

        // 3. Answer with the stored row
        return detail(accountId);
    }

    private void applyContact(Account account, UpdateEmployeeRequest request) {
        if (StringUtils.hasText(request.email())) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (accountRepository.existsByEmailIgnoreCaseAndIdNot(email, account.getId())) {
                throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", email);
            }
            account.setEmail(email);
        }
        if (StringUtils.hasText(request.phone())) {
            String phone = request.phone().trim();
            if (accountRepository.existsByPhoneAndIdNot(phone, account.getId())) {
                throw new DuplicateResourceException(IdentityErrorCode.PHONE_ALREADY_EXISTS, "Account", "phone", phone);
            }
            account.setPhone(phone);
        }
    }

    private void trimNames(Employee employee) {
        employee.setFirstName(employee.getFirstName().trim());
        employee.setLastName(employee.getLastName().trim());
    }

    private EmployeeResponse detail(UUID accountId) {
        EmployeeResponse employee = employeeRepository.findDetail(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_EMPLOYEE, accountId));
        return withUrl(employee, avatarResolver.resolveUrls(Collections.singletonList(employee.avatarFileId())));
    }

    private EmployeeResponse withUrl(EmployeeResponse employee, Map<UUID, String> urls) {
        return employee.avatarFileId() == null ? employee : employee.withAvatarUrl(urls.get(employee.avatarFileId()));
    }
}
