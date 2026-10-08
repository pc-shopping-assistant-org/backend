package com.ecm.order.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {

    CART_EMPTY(HttpStatus.BAD_REQUEST, "Cart has no items"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Cart item not found"),
    CART_OWNER_REQUIRED(HttpStatus.BAD_REQUEST, "An authenticated customer is required"),
    CART_QUANTITY_TOO_LARGE(HttpStatus.BAD_REQUEST, "A cart line holds at most 9999 units"),
    INVALID_SHIPPING_METHOD(HttpStatus.BAD_REQUEST, "Shipping method does not exist or is not available"),
    INVALID_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "Payment method does not exist or is not available"),
    INVALID_RECIPIENT(HttpStatus.BAD_REQUEST, "Provide either a saved address or the recipient name, phone and address"),
    DISCOUNT_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "The discount code cannot be applied to this order"),
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
