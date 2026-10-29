package com.ecm.identity.helper;

import com.ecm.common.exception.InvalidStateException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Locks an account and cuts off the tokens already issued for it. Shared by the employee and customer
 * management use cases; the caller's transaction makes a failed revocation roll the lock back.
 */
@Component
@RequiredArgsConstructor
public class AccountLocker {

    private final AccountRepository accountRepository;
    private final TokenRevocationService tokenRevocationService;
    private final JwtProperties jwtProperties;

    public void lock(Account account, String resourceName) {
        // A deleted account is gone for good, so locking it would bring it back as a locked one
        if (account.getStatus() == AccountStatus.LOCKED || account.getStatus() == AccountStatus.DELETED) {
            throw new InvalidStateException(resourceName, account.getId(), account.getStatus().name(), "lock");
        }
        account.setStatus(AccountStatus.LOCKED);
        accountRepository.save(account);
        tokenRevocationService.revokeBefore(account.getId(), Instant.now().plusSeconds(1),
                Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs()));
    }
}
