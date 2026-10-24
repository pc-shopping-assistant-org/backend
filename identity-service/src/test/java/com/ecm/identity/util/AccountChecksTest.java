package com.ecm.identity.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.exception.IdentityErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountChecksTest {

    private static Account account(AccountStatus status) {
        return Account.builder().status(status).build();
    }

    @Test
    void anActiveAccountMayBeUsed() {
        assertDoesNotThrow(() -> AccountChecks.ensureUsable(account(AccountStatus.ACTIVE)));
    }

    @Test
    void aLockedAccountIsRefusedAsLocked() {
        BusinessException ex = assertThrows(BusinessException.class, () -> AccountChecks.ensureUsable(account(AccountStatus.LOCKED)));

        assertEquals(IdentityErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    }

    @Test
    void anyOtherNonActiveAccountIsRefusedAsInactive() {
        BusinessException ex = assertThrows(BusinessException.class, () -> AccountChecks.ensureUsable(account(AccountStatus.INACTIVE)));

        assertEquals(IdentityErrorCode.ACCOUNT_INACTIVE, ex.getErrorCode());
    }
}
