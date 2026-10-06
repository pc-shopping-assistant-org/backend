package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.request.CreateProductRequest;
import com.ecm.catalog.dto.request.CreateVariantRequest;
import com.ecm.catalog.dto.request.UpdateProductRequest;
import com.ecm.catalog.dto.request.UpdateProductStatusRequest;
import com.ecm.catalog.dto.request.UpdateVariantRequest;
import com.ecm.catalog.dto.request.UpdateVariantStatusRequest;
import com.ecm.catalog.entity.CatalogStatus;
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

    @PostMapping
    public ApiResponse<ProductDetailResponse> createProduct(@Valid @RequestBody CreateProductRequest request,
                                                             org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.createProduct(request, employeeId(authentication)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request,
                                                             org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.updateProduct(id, request, employeeId(authentication)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductDetailResponse> updateProductStatus(@PathVariable UUID id, @Valid @RequestBody UpdateProductStatusRequest request,
                                                                   org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.updateProductStatus(id, request.status(), employeeId(authentication)));
    }

    @PostMapping("/{id}/variants")
    public ApiResponse<ProductVariantResponse> createVariant(@PathVariable UUID id, @Valid @RequestBody CreateVariantRequest request,
                                                              org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.createVariant(id, request, employeeId(authentication)));
    }

    @PutMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(@PathVariable UUID id, @PathVariable UUID variantId,
                                                              @Valid @RequestBody UpdateVariantRequest request,
                                                              org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.updateVariant(id, variantId, request, employeeId(authentication)));
    }

    @PatchMapping("/{id}/variants/{variantId}/status")
    public ApiResponse<ProductVariantResponse> updateVariantStatus(@PathVariable UUID id, @PathVariable UUID variantId,
                                                                   @Valid @RequestBody UpdateVariantStatusRequest request,
                                                                   org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(productService.updateVariantStatus(id, variantId, request.status(), employeeId(authentication)));
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        productService.deleteVariant(id, variantId);
        return ApiResponse.success(null);
    }

    private UUID employeeId(org.springframework.security.core.Authentication authentication) {
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            try { return UUID.fromString(jwt.getToken().getClaimAsString("accountId")); }
            catch (IllegalArgumentException | NullPointerException ignored) { return null; }
        }
        return null;
    }

    @GetMapping
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getProducts(
            @Valid @ModelAttribute ProductFilterRequest filter
    ) {
        CursorPageResponse<ProductSummaryResponse> response = productService.getProducts(filter);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProductById(@PathVariable UUID id) {
        ProductDetailResponse response = productService.getProductById(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/slug/{seoName}")
    public ApiResponse<ProductDetailResponse> getProductBySeoName(@PathVariable String seoName) {
        ProductDetailResponse response = productService.getProductBySeoName(seoName);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> getProductVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId
    ) {
        ProductVariantResponse response = productService.getProductVariant(id, variantId);
        return ApiResponse.success(response);
    }
}
