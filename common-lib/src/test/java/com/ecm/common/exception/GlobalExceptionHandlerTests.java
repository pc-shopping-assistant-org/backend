package com.ecm.common.exception;

import com.ecm.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uploadAboveTheLimitIsReportedAsPayloadTooLargeInsteadOfAServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessage()).isEqualTo(CommonErrorCode.PAYLOAD_TOO_LARGE.name());
    }
}
