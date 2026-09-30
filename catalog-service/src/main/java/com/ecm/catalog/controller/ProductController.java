package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.response.CursorPageResponse;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.service.ProductService;
import com.ecm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getProducts(
            @Valid @ModelAttribute ProductFilterRequest filter
    ) {
        CursorPageResponse<ProductSummaryResponse> response = productService.getProducts(filter);
        return ApiResponse.success("Get products successfully", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProductById(@PathVariable UUID id) {
        ProductDetailResponse response = productService.getProductById(id);
        return ApiResponse.success("Get product successfully", response);
    }

    @GetMapping("/slug/{seoName}")
    public ApiResponse<ProductDetailResponse> getProductBySeoName(@PathVariable String seoName) {
        ProductDetailResponse response = productService.getProductBySeoName(seoName);
        return ApiResponse.success("Get product successfully", response);
    }

    @GetMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> getProductVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId
    ) {
        ProductVariantResponse response = productService.getProductVariant(id, variantId);
        return ApiResponse.success("Get product variant successfully", response);
    }
}
