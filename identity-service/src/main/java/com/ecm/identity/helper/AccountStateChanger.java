package com.ecm.identity.service;

import com.ecm.common.exception.InvalidStateException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The other moves of an account between its states, next to {@link AccountLocker}: switching it off and on again, and
 * deleting it. A deleted account stays in the table (its email and phone stay taken), and nothing brings it back.
 * Anything that stops the account from signing in also cuts off the tokens already issued; the caller's transaction
 * makes a failed revocation roll the change back.
 */
@Component
@RequiredArgsConstructor
public class AccountStateChanger {

    private final AccountRepository accountRepository;
    private final TokenRevocationService tokenRevocationService;
    private final JwtProperties jwtProperties;

    /** An active account is switched off; a locked one stays locked, and a deleted one is gone. */
    public void deactivate(Account account, String resourceName) {
        requireState(account, resourceName, "deactivate", AccountStatus.ACTIVE);
        change(account, AccountStatus.INACTIVE);
        revokeTokens(account);
    }

    /** Only an account that was switched off comes back; unlocking is not a move this system has. */
    public void activate(Account account, String resourceName) {
        requireState(account, resourceName, "activate", AccountStatus.INACTIVE);
        change(account, AccountStatus.ACTIVE);
    }

    /** Whatever the account is now, except already deleted. */
    public void delete(Account account, String resourceName) {
        requireState(account, resourceName, "delete", AccountStatus.ACTIVE, AccountStatus.INACTIVE, AccountStatus.LOCKED);
        change(account, AccountStatus.DELETED);
        revokeTokens(account);
    }

    private void requireState(Account account, String resourceName, String action, AccountStatus... allowed) {
        for (AccountStatus status : allowed) {
            if (account.getStatus() == status) {
                return;
            }
        }
        throw new InvalidStateException(resourceName, account.getId(), account.getStatus().name(), action);
    }

    private void change(Account account, AccountStatus status) {
        account.setStatus(status);
        accountRepository.save(account);
    }

    private void revokeTokens(Account account) {
        tokenRevocationService.revokeBefore(account.getId(), Instant.now().plusSeconds(1),
                Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs()));
    }
}
