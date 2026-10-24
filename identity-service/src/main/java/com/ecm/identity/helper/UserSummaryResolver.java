package com.ecm.identity.helper;

import com.ecm.common.exception.ResourceNotFoundException;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Role;
import com.ecm.identity.mapper.AdminMapper;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.mapper.EmployeeMapper;
import com.ecm.identity.repository.AdminRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the profile summary of an account from whichever profile table (customer, employee, admin) holds it. */
@Component
@RequiredArgsConstructor
public class UserSummaryResolver {

    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final AdminRepository adminRepository;
    private final CustomerMapper customerMapper;
    private final EmployeeMapper employeeMapper;
    private final AdminMapper adminMapper;

    public UserSummaryResponse resolve(Account account, Role role) {
        return customerRepository.findById(account.getId())
                .map(customer -> customerMapper.toSummary(account, role, customer))
                .or(() -> employeeRepository.findById(account.getId())
                        .map(employee -> employeeMapper.toSummary(account, role, employee)))
                .or(() -> adminRepository.findById(account.getId())
                        .map(admin -> adminMapper.toSummary(account, role, admin)))
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", account.getId()));
    }
}
