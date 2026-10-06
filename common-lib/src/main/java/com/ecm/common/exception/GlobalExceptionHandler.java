package com.ecm.common.exception;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.tracing.TraceSupport;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("Business exception with server error status {}", errorCode.name(), ex);
            TraceSupport.recordError(ex);
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiResponse.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiResponse.ErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ApiResponse.ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiResponse.ErrorDetail(
                        lastPathNode(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.VALIDATION_ERROR, details));
    }

    private static String lastPathNode(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
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
                .body(ApiResponse.error(CommonErrorCode.FORBIDDEN, CommonErrorCode.FORBIDDEN.getDefaultMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED.getDefaultMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(CommonErrorCode.NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.getDefaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        TraceSupport.recordError(ex);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
