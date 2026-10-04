package com.ecm.catalog.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CatalogErrorCode implements ErrorCode {

    INVALID_PRICE_RANGE(HttpStatus.BAD_REQUEST, "Invalid price range"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "Catalog resource conflicts with existing data"),
    INVALID_CATALOG_REFERENCE(HttpStatus.BAD_REQUEST, "Referenced catalog resource does not exist or is inactive"),
    PRODUCT_IN_USE(HttpStatus.CONFLICT, "Product has order history and cannot be deleted"),
    INVALID_PRODUCT_STATUS(HttpStatus.BAD_REQUEST, "Product status transition is invalid");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CatalogErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getDefaultMessage() {
        return this.defaultMessage;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
