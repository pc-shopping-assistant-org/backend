package com.ecm.identity.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.entity.Customer;
import com.ecm.identity.entity.Role;
import com.ecm.identity.event.GoogleAvatarRequested;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.repository.CustomerRepository;
import com.ecm.identity.repository.RoleRepository;
import com.ecm.identity.service.google.GoogleIdentity;
import com.ecm.identity.util.AccountChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Attaches a verified Google identity to the local account with the same email, or signs up a new customer. */
@Component
@RequiredArgsConstructor
public class GoogleAccountLinker {

    private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public Account linkOrSignUp(GoogleIdentity identity) {
        return accountRepository.findByEmailIgnoreCase(identity.email())
                .map(existing -> link(existing, identity))
                .orElseGet(() -> signUp(identity));
    }

    private Account link(Account account, GoogleIdentity identity) {
        // Prevent re-linking if another Google identity is already bound
        if (StringUtils.hasText(account.getGoogleSubject())
                && !identity.subject().equals(account.getGoogleSubject())) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        AccountChecks.ensureUsable(account);

        account.setGoogleSubject(identity.subject());
        return accountRepository.save(account);
    }

    /** Creates a customer with no password and no phone; never any other role. */
    private Account signUp(GoogleIdentity identity) {
        Role customerRole = roleRepository.findByName(ROLE_CUSTOMER)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        try {
            // Its own transaction, so the picture import only starts once the signup is really saved
            return transactionTemplate.execute(status -> {
                Account account = accountRepository.save(Account.builder()
                        .email(identity.email())
                        .googleSubject(identity.subject())
                        .roleId(customerRole.getId())
                        .status(AccountStatus.ACTIVE)
                        .build());
                customerRepository.save(Customer.builder()
                        .accountId(account.getId())
                        .firstName(identity.firstName())
                        .lastName(identity.lastName())
                        .build());
                if (StringUtils.hasText(identity.pictureUrl())) {
                    eventPublisher.publishEvent(
                            new GoogleAvatarRequested(account.getId(), account.getEmail(), identity.pictureUrl()));
                }
                return account;
            });
        } catch (DataIntegrityViolationException ex) {
            // Another request signed this person up first (double click, two tabs): use theirs
            return accountRepository.findByGoogleSubject(identity.subject())
                    .or(() -> accountRepository.findByEmailIgnoreCase(identity.email()))
                    .map(existing -> link(existing, identity))
                    .orElseThrow(() -> ex);
        }
    }
}
