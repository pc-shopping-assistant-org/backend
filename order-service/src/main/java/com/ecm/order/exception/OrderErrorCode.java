package com.ecm.order.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {

    CART_NOT_ACTIVE(HttpStatus.CONFLICT, "Cart is not active"),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "Cart has no items"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found"),
    CART_OWNER_REQUIRED(HttpStatus.BAD_REQUEST, "Provide exactly one authenticated account or guest cart session"),
    CART_SESSION_REQUIRED(HttpStatus.BAD_REQUEST, "Guest cart session is required"),
    CART_QUANTITY_TOO_LARGE(HttpStatus.BAD_REQUEST, "Cart quantity or amount is too large"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "One or more items do not have enough stock"),
    VARIANT_NOT_AVAILABLE(HttpStatus.CONFLICT, "One or more items are no longer available"),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Order cannot be cancelled in its current status"),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.CONFLICT, "Order status transition is invalid");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    OrderErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getDefaultMessage() { return defaultMessage; }

    @Override
    public HttpStatus getHttpStatus() { return httpStatus; }
}
