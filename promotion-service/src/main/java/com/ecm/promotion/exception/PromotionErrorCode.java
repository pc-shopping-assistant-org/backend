package com.ecm.promotion.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PromotionErrorCode implements ErrorCode {
    DISCOUNT_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Discount code already exists"),
    INVALID_DISCOUNT_TARGETS(HttpStatus.BAD_REQUEST, "Discount targets do not match its application scope"),
    INVALID_DISCOUNT_CODE(HttpStatus.BAD_REQUEST, "Only an ORDER discount can have a code"),
    INVALID_DISCOUNT_STATUS(HttpStatus.BAD_REQUEST, "A discount can only be set to ACTIVE or INACTIVE"),
    INVALID_DISCOUNT_CATEGORY(HttpStatus.BAD_REQUEST, "Discount target category does not exist or is not in use"),
    DISCOUNT_RUNNING(HttpStatus.CONFLICT, "A running discount cannot be deleted; lock it first"),
    DISCOUNT_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "Discount is not applicable"),
    DISCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, "Discount is not currently active"),
    MINIMUM_ORDER_NOT_MET(HttpStatus.BAD_REQUEST, "Minimum order amount is not met"),
    INVALID_DISCOUNT_VALUE(HttpStatus.BAD_REQUEST, "Discount value or date range is invalid");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    PromotionErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override public String getDefaultMessage() { return defaultMessage; }
    @Override public HttpStatus getHttpStatus() { return httpStatus; }
}
