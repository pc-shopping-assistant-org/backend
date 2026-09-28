package com.ecm.order.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {

    CART_NOT_ACTIVE(HttpStatus.CONFLICT, "Cart is not active"),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "Cart has no items"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "One or more items do not have enough stock"),
    VARIANT_NOT_AVAILABLE(HttpStatus.CONFLICT, "One or more items are no longer available");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    OrderErrorCode(HttpStatus httpStatus, String defaultMessage) {
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
