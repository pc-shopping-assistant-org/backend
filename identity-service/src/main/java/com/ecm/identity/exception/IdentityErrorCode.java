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
    ROLE_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "Default role is not configured");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    IdentityErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getCode() {
        return name();
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
