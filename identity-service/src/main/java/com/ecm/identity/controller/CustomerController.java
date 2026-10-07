package com.ecm.identity.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.dto.response.CustomerDetailResponse;
import com.ecm.identity.dto.response.CustomerResponse;
import com.ecm.identity.dto.response.CustomerSummaryResponse;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

// Restricted to ROLE_EMPLOYEE by SecurityConfig.
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ApiResponse<PageResponse<CustomerResponse>> search(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) AccountStatus status,
                                                              @RequestParam(required = false) Instant createdFrom,
                                                              @RequestParam(required = false) Instant createdTo,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(customerService.search(keyword, status, createdFrom, createdTo, page, size));
    }

    // Restricted to ROLE_ADMIN by SecurityConfig.
    @GetMapping("/summary")
    public ApiResponse<CustomerSummaryResponse> getSummary() {
        return ApiResponse.success(customerService.getSummary());
    }

    @GetMapping("/{accountId}")
    public ApiResponse<CustomerDetailResponse> getById(@PathVariable UUID accountId) {
        return ApiResponse.success(customerService.getById(accountId));
    }

    @PostMapping("/{accountId}/lock")
    public ApiResponse<CustomerResponse> lock(@PathVariable UUID accountId) {
        return ApiResponse.success(customerService.lock(accountId));
    }
}
