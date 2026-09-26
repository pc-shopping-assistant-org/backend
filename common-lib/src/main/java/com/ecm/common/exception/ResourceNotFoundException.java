package com.ecm.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(CommonErrorCode.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(CommonErrorCode.NOT_FOUND, resourceName + " not found with identifier: " + identifier);
    }
}
