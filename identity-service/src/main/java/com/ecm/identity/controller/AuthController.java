package com.ecm.identity.controller;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.response.ApiResponse;
import com.ecm.identity.config.JwtAuthenticationFilter;
import com.ecm.identity.config.UserPrincipal;
import com.ecm.identity.dto.request.ChangePasswordRequest;
import com.ecm.identity.dto.request.ForgotPasswordRequest;
import com.ecm.identity.dto.request.GoogleLoginRequest;
import com.ecm.identity.dto.request.LoginRequest;
import com.ecm.identity.dto.request.RegisterRequest;
import com.ecm.identity.dto.request.ResendOtpRequest;
import com.ecm.identity.dto.request.ResetPasswordRequest;
import com.ecm.identity.dto.request.VerifyOtpRequest;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.dto.response.LogoutResponse;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/resend-otp")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/verify-otp")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.success(authService.verifyRegistrationOtp(request));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.success(authService.loginWithGoogle(request));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(@RequestHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER) String authHeader) {
        if (authHeader == null || !authHeader.startsWith(JwtAuthenticationFilter.BEARER_PREFIX)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        String token = authHeader.substring(JwtAuthenticationFilter.BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        boolean revoked = authService.logout(token);
        return ApiResponse.success(new LogoutResponse(revoked));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.success(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.otp(), request.newPassword());
        return ApiResponse.success(null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        authService.changePassword(principal.getAccountId(), request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
