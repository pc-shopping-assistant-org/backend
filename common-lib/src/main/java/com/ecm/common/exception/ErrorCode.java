package com.ecm.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract for error codes usable in {@link BusinessException}. Each service defines its
 * own enum implementing this interface for domain-specific errors; {@link CommonErrorCode}
 * covers cross-cutting cases shared by every service.
 */
public interface ErrorCode {

    /**
     * Stable machine-readable code, e.g. "USER_ALREADY_EXISTS". Defaults to the enum constant
     * name, which is sufficient for enum-based implementations.
     */
    String getCode();

    String getDefaultMessage();

    HttpStatus getHttpStatus();
}
