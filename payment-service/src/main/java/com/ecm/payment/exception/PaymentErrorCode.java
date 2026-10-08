package com.ecm.payment.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_STATUS_NOT_EDITABLE(HttpStatus.CONFLICT, "Payment status cannot be changed this way"),
    WEBHOOK_NOT_ALLOWED(HttpStatus.CONFLICT, "Only an online payment is settled by the payment gateway"),
    DUPLICATE_TRANSACTION_CODE(HttpStatus.CONFLICT, "Another payment already has this transaction code"),
    PAYMENT_METHOD_NOT_FOUND(HttpStatus.BAD_REQUEST, "Payment method does not exist");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    PaymentErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getDefaultMessage() { return defaultMessage; }

    @Override
    public HttpStatus getHttpStatus() { return httpStatus; }
}
