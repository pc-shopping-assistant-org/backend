package com.ecm.common.exception;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.tracing.TraceSupport;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("Business exception with server error status {}", errorCode.getCode(), ex);
            TraceSupport.recordError(ex);
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(),
                        CommonErrorCode.VALIDATION_ERROR.getDefaultMessage(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR.getCode(), ex.getMessage()));
    }

    /*
     * Note: security filter-chain rejections (e.g. authorizeHttpRequests denying a request
     * before it reaches the DispatcherServlet) are handled by Spring Security's own
     * AccessDeniedHandler/AuthenticationEntryPoint, not by this @ControllerAdvice. These
     * two handlers cover exceptions thrown from inside controller/service code, e.g. a
     * @PreAuthorize check on a method or a manual throw.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(CommonErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.FORBIDDEN.getCode(), CommonErrorCode.FORBIDDEN.getDefaultMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.UNAUTHORIZED.getCode(), CommonErrorCode.UNAUTHORIZED.getDefaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        TraceSupport.recordError(ex);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
