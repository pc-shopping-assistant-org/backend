package com.ecm.catalog.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.service.ProductVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/product-variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @GetMapping("/{id}")
    public ApiResponse<ProductVariantResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(productVariantService.getById(id));
    }
}
