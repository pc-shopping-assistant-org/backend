package com.ecm.common.exception;

import java.time.Instant;

/**
 * A resource that existed but is no longer usable because it expired (OTP, auth token,
 * promotion code, payment session, ...). The shape of the error is identical across
 * services; only the {@link ErrorCode} and resource name differ, so services should
 * still supply their own {@link ErrorCode} when the specific code matters to API
 * consumers, falling back to {@link CommonErrorCode#EXPIRED} otherwise.
 */
public class ExpiredException extends BusinessException {

    public ExpiredException(String message) {
        super(CommonErrorCode.EXPIRED, message);
    }

    public ExpiredException(String resourceName, Object identifier) {
        super(CommonErrorCode.EXPIRED, resourceName + " '" + identifier + "' has expired");
    }

    public ExpiredException(String resourceName, Object identifier, Instant expiredAt) {
        super(CommonErrorCode.EXPIRED, resourceName + " '" + identifier + "' expired at " + expiredAt);
    }

    public ExpiredException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ExpiredException(ErrorCode errorCode, String resourceName, Object identifier) {
        super(errorCode, resourceName + " '" + identifier + "' has expired");
    }
}
