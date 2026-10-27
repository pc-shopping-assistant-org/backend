package com.ecm.order.util;

import com.ecm.common.security.CurrentUser;
import com.ecm.order.exception.OrderErrorCode;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/** The customer behind a cart or order request; the use case has its own error when no customer is signed in. */
public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    public static UUID id(Authentication authentication) {
        return CurrentUser.requireAccountId(authentication, OrderErrorCode.CART_OWNER_REQUIRED);
    }
}
