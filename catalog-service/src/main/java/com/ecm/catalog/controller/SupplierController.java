package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.SupplierRequest;
import com.ecm.catalog.dto.request.UpdateSupplierRequest;
import com.ecm.catalog.dto.response.SupplierResponse;
import com.ecm.catalog.service.SupplierService;
import com.ecm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService supplierService;

    @GetMapping
    public ApiResponse<List<SupplierResponse>> getAll() {
        return ApiResponse.success(supplierService.getActiveSuppliers());
    }

    @PostMapping
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.success(supplierService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupplierResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest request) {
        return ApiResponse.success(supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        supplierService.delete(id);
        return ApiResponse.success(null);
    }
}
