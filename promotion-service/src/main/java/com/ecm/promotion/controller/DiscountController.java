package com.ecm.promotion.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.dto.request.CreateDiscountRequest;
import com.ecm.promotion.dto.response.ApplyDiscountResponse;
import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.DiscountStatus;
import com.ecm.promotion.service.DiscountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {
    private static final String ACCOUNT_ID_CLAIM = "accountId";
    private final DiscountService discountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscountResponse> create(@Valid @RequestBody CreateDiscountRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.create(request, actorId(jwt)));
    }

    @GetMapping
    public ApiResponse<PageResponse<DiscountResponse>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(discountService.list(page, size));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiscountResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateDiscountRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.update(id, request, actorId(jwt)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscountResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(discountService.get(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DiscountResponse> setStatus(
            @PathVariable UUID id, @RequestParam DiscountStatus status, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.setStatus(id, status, actorId(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        discountService.delete(id);
    }

    @PostMapping("/apply")
    public ApiResponse<ApplyDiscountResponse> apply(@Valid @RequestBody ApplyDiscountRequest request) {
        return ApiResponse.success(discountService.apply(request));
    }

    private UUID actorId(Jwt jwt) {
        String accountId = jwt.getClaimAsString(ACCOUNT_ID_CLAIM);
        return accountId == null ? null : UUID.fromString(accountId);
    }
}
