package com.ecm.promotion.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.promotion.dto.request.ApplyDiscountRequest;
import com.ecm.promotion.dto.request.CreateDiscountRequest;
import com.ecm.promotion.dto.request.UpdateDiscountRequest;
import com.ecm.promotion.dto.request.UpdateDiscountStatusRequest;
import com.ecm.promotion.dto.response.ApplyDiscountResponse;
import com.ecm.promotion.dto.response.DiscountResponse;
import com.ecm.promotion.entity.DiscountState;
import com.ecm.promotion.service.DiscountApplyService;
import com.ecm.promotion.service.DiscountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private static final String ACCOUNT_ID_CLAIM = "accountId";
    private static final String DEFAULT_PAGE = "0";
    private static final String DEFAULT_PAGE_SIZE = "20";

    private final DiscountService discountService;
    private final DiscountApplyService discountApplyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DiscountResponse> create(@Valid @RequestBody CreateDiscountRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.create(request, employeeId(jwt)));
    }

    @GetMapping
    public ApiResponse<PageResponse<DiscountResponse>> list(@RequestParam(defaultValue = DEFAULT_PAGE) int page,
                                                            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
                                                            @RequestParam(required = false) DiscountState state) {
        return ApiResponse.success(discountService.list(page, size, state));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiscountResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(discountService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiscountResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateDiscountRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.update(id, request, employeeId(jwt)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DiscountResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateDiscountStatusRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(discountService.updateStatus(id, request.status(), employeeId(jwt)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        discountService.delete(id, employeeId(jwt));
    }

    @PostMapping("/apply")
    public ApiResponse<ApplyDiscountResponse> apply(@Valid @RequestBody ApplyDiscountRequest request) {
        return ApiResponse.success(discountApplyService.apply(request));
    }

    private UUID employeeId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(ACCOUNT_ID_CLAIM));
    }
}
