package com.ecm.media.exception;

import com.ecm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MediaErrorCode implements ErrorCode {
    INVALID_FILE(HttpStatus.BAD_REQUEST, "File must be a non-empty image"),
    CLOUDINARY_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "Image upload failed");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    MediaErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
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
