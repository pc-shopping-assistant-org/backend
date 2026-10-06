package com.ecm.identity.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.identity.config.UserPrincipal;
import com.ecm.identity.dto.request.UpdateProfileRequest;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<UserSummaryResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(profileService.getProfile(principal.getAccountId()));
    }

    @PutMapping
    public ApiResponse<UserSummaryResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(profileService.updateProfile(principal.getAccountId(), request));
    }
}
