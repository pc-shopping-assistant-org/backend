package com.ecm.identity.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Domain-specific error codes for identity-service. Each service defines its own enum like
 * this instead of adding business errors to the shared {@code CommonErrorCode} in common-lib.
 */
public enum IdentityErrorCode implements ErrorCode {

    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number already exists"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Invalid or expired OTP code"),
    ROLE_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "Default role is not configured"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Account is not active"),
    GOOGLE_ACCOUNT_NOT_LINKED(HttpStatus.UNAUTHORIZED, "Google account is not linked to any local account"),
    PHONE_ALREADY_IN_USE(HttpStatus.CONFLICT, "Phone number is already in use by another account"),
    CUSTOMER_PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "Only customer profiles can be updated"),
    INCORRECT_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    IdentityErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }


    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
