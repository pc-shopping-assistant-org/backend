package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.request.CreateBrandRequest;
import com.ecm.catalog.dto.request.UpdateBrandRequest;
import com.ecm.catalog.dto.request.UpdateStatusRequest;
import com.ecm.catalog.service.BrandService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @PostMapping
    public ApiResponse<BrandResponse> create(@jakarta.validation.Valid @RequestBody CreateBrandRequest request) {
        return ApiResponse.success(brandService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandResponse> update(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateBrandRequest request) {
        return ApiResponse.success(brandService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<BrandResponse> updateStatus(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.success(brandService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        brandService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/admin")
    public ApiResponse<List<BrandResponse>> getAllBrandsForAdmin() {
        return ApiResponse.success(brandService.getAllBrandsForAdmin());
    }

    @GetMapping
    public ApiResponse<List<BrandResponse>> getAllBrands() {
        return ApiResponse.success(brandService.getAllBrands());
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrandById(@PathVariable UUID id) {
        return ApiResponse.success(brandService.getBrandById(id));
    }
}
