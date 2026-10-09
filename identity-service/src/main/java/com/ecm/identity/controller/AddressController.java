package com.ecm.identity.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.identity.config.UserPrincipal;
import com.ecm.identity.dto.request.CreateAddressRequest;
import com.ecm.identity.dto.request.UpdateAddressRequest;
import com.ecm.identity.dto.response.AddressResponse;
import com.ecm.identity.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(addressService.list(principal.getAccountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AddressResponse> create(
            @Valid @RequestBody CreateAddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(addressService.create(principal.getAccountId(), request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<AddressResponse> update(
            @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(addressService.update(principal.getAccountId(), addressId, request));
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<AddressResponse> setDefault(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(addressService.setDefault(principal.getAccountId(), addressId));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID addressId,
            @AuthenticationPrincipal UserPrincipal principal) {
        addressService.delete(principal.getAccountId(), addressId);
        return ApiResponse.success(null);
    }
}
