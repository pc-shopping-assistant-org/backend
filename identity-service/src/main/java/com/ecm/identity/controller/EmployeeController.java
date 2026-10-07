package com.ecm.identity.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.identity.dto.request.CreateEmployeeRequest;
import com.ecm.identity.dto.request.UpdateEmployeeRequest;
import com.ecm.identity.dto.response.EmployeeResponse;
import com.ecm.identity.entity.AccountStatus;
import com.ecm.identity.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Restricted to ROLE_ADMIN by SecurityConfig.
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ApiResponse.success(employeeService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<EmployeeResponse>> search(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) AccountStatus status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(employeeService.search(keyword, status, page, size));
    }

    @GetMapping("/{accountId}")
    public ApiResponse<EmployeeResponse> getById(@PathVariable UUID accountId) {
        return ApiResponse.success(employeeService.getById(accountId));
    }

    @PatchMapping("/{accountId}")
    public ApiResponse<EmployeeResponse> update(@PathVariable UUID accountId,
                                                @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.success(employeeService.update(accountId, request));
    }

    @PostMapping("/{accountId}/lock")
    public ApiResponse<EmployeeResponse> lock(@PathVariable UUID accountId) {
        return ApiResponse.success(employeeService.lock(accountId));
    }
}
