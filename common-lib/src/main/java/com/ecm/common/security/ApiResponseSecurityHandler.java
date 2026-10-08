package com.ecm.common.security;

import com.ecm.common.exception.CommonErrorCode;
import com.ecm.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the standard {@code ApiResponse} envelope for security filter-chain rejections, which
 * never reach {@code GlobalExceptionHandler} because they happen before the DispatcherServlet.
 */
public final class ApiResponseSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final ApiResponseSecurityHandler INSTANCE = new ApiResponseSecurityHandler();

    private static final String BEARER_CHALLENGE = "Bearer";

    private ApiResponseSecurityHandler() {
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
        write(response, CommonErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, CommonErrorCode.FORBIDDEN);
    }

    private static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Fixed, static strings only: no escaping needed and no ObjectMapper dependency.
        response.getWriter().write("{\"data\":null,\"message\":\"" + errorCode.name()
                + "\",\"errors\":[{\"message\":\"" + errorCode.getDefaultMessage() + "\"}]}");
    }
}
