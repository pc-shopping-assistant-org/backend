package com.ecm.common.exception;

/**
 * Counterpart to {@link ResourceNotFoundException} for "already exists" conflicts
 * (duplicate username, SKU, promotion code, ...). The shape of the error is identical
 * across services; only the {@link ErrorCode} and field names differ, so services should
 * still supply their own {@link ErrorCode} when the specific code matters to API
 * consumers, falling back to {@link CommonErrorCode#CONFLICT} otherwise.
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(CommonErrorCode.CONFLICT, message);
    }

    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(CommonErrorCode.CONFLICT, resourceName + " with " + field + " '" + value + "' already exists");
    }

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DuplicateResourceException(ErrorCode errorCode, String resourceName, String field, Object value) {
        super(errorCode, resourceName + " with " + field + " '" + value + "' already exists");
    }
}
