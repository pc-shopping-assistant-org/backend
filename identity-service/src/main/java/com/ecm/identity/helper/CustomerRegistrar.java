package com.ecm.identity.helper;

import com.ecm.common.exception.DuplicateResourceException;
import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.CustomerAddress;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.mapper.CustomerMapper;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerAddressRepository;
import com.ecm.identity.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Writes the rows of a newly verified registration: the account, the customer profile and the default address. */
@Component
@RequiredArgsConstructor
public class CustomerRegistrar {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;

    public record Registered(Account account, Customer customer) {
    }

    public Registered register(RegisterRequest pending, Role role) {
        Account account = Account.builder()
                .email(pending.email())
                .phone(pending.phone())
                .passwordHash(passwordEncoder.encode(pending.password()))
                .roleId(role.getId())
                .status(AccountStatus.ACTIVE)
                .build();
        try {
            account = accountRepository.save(account);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(IdentityErrorCode.EMAIL_ALREADY_EXISTS, "Account", "email", pending.email());
        }

        Customer customer = customerMapper.toEntity(pending);
        customer.setAccountId(account.getId());
        customer = customerRepository.save(customer);

        customerAddressRepository.save(CustomerAddress.builder()
                .customerId(customer.getAccountId())
                .recipientName(pending.lastName() + " " + pending.firstName())
                .phone(pending.phone())
                .addressLine(pending.address())
                .isDefault(true)
                .build());

        return new Registered(account, customer);
    }
}
