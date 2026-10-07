package com.ecm.catalog.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CatalogErrorCode implements ErrorCode {

    INVALID_PRICE_RANGE(HttpStatus.BAD_REQUEST, "Invalid price range"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "Catalog resource conflicts with existing data"),
    INVALID_CATALOG_REFERENCE(HttpStatus.BAD_REQUEST, "Referenced catalog resource does not exist or is inactive"),
    PRODUCT_IN_USE(HttpStatus.CONFLICT, "Product has stock or order history and cannot be deleted"),
    INVALID_PRODUCT_STATUS(HttpStatus.BAD_REQUEST, "Product status transition is invalid"),
    REVIEW_ORDER_NOT_COMPLETED(HttpStatus.CONFLICT, "Only completed orders can be reviewed"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "This order item has already been reviewed"),
    REVIEW_PRODUCT_MISMATCH(HttpStatus.BAD_REQUEST, "The order item does not belong to this product"),
    REVIEW_ALREADY_EDITED(HttpStatus.CONFLICT, "This review has already been edited once"),
    REVIEW_EDIT_WINDOW_EXPIRED(HttpStatus.CONFLICT, "A review can only be edited within 30 days of posting"),
    REVIEW_PRODUCT_UNAVAILABLE(HttpStatus.CONFLICT, "The product is no longer on sale, so the review can no longer be edited"),
    INVALID_SPECIFICATIONS(HttpStatus.BAD_REQUEST, "Product specifications do not match the category attributes"),
    INVALID_ATTRIBUTE_DEFINITION(HttpStatus.BAD_REQUEST, "Attribute definition is invalid"),
    ATTRIBUTE_IN_USE(HttpStatus.CONFLICT, "Attribute is used by a category template and cannot be deleted"),
    INVALID_CATEGORY_ATTRIBUTES(HttpStatus.BAD_REQUEST, "Category attribute template is invalid"),
    INVALID_MEDIA_FILE(HttpStatus.BAD_REQUEST, "Image file does not exist"),
    INVALID_GALLERY(HttpStatus.BAD_REQUEST, "Product gallery is invalid"),
    INVALID_VARIANT_OPTIONS(HttpStatus.BAD_REQUEST, "Variant options are invalid");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    CatalogErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
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
