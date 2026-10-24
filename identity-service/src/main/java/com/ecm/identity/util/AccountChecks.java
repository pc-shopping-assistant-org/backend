package com.ecm.identity.util;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.exception.IdentityErrorCode;

/** Checks on the state of an account that throw when it may not be used to sign in. */
public final class AccountChecks {

    private AccountChecks() {
    }

    public static void ensureUsable(Account account) {
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_LOCKED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_INACTIVE);
        }
    }
}
