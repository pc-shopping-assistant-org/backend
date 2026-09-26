package com.ecm.common.exception;

/**
 * Wraps a failure calling another service (timeout, connection refused, 5xx response, ...).
 * Use this at the boundary where a service calls another one (RestTemplate/Feign/WebClient)
 * instead of letting the raw HTTP client exception bubble up to the caller.
 */
public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String serviceName, String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, serviceName + ": " + message);
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, serviceName + ": " + message, cause);
    }

    public ExternalServiceException(String serviceName, Throwable cause) {
        this(serviceName, cause.getMessage(), cause);
    }
}
