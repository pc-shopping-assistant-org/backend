package com.ecm.catalog.controller;

import com.ecm.catalog.dto.request.CreateProductRequest;
import com.ecm.catalog.dto.request.CreateVariantRequest;
import com.ecm.catalog.dto.request.ProductFilterRequest;
import com.ecm.catalog.dto.request.UpdateProductRequest;
import com.ecm.catalog.dto.request.UpdateProductStatusRequest;
import com.ecm.catalog.dto.request.UpdateVariantRequest;
import com.ecm.catalog.dto.request.UpdateVariantStatusRequest;
import com.ecm.catalog.dto.response.CursorPageResponse;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.dto.response.ProductSummaryResponse;
import com.ecm.catalog.dto.response.ProductVariantResponse;
import com.ecm.catalog.service.ProductQueryService;
import com.ecm.catalog.service.ProductService;
import com.ecm.catalog.service.ProductVariantService;
import com.ecm.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String ACCOUNT_ID_CLAIM = "accountId";

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductQueryService productQueryService;

    @PostMapping
    public ApiResponse<ProductDetailResponse> createProduct(@Valid @RequestBody CreateProductRequest request,
                                                            Authentication authentication) {
        return ApiResponse.success(productService.createProduct(request, employeeId(authentication)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductDetailResponse> updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request,
                                                            Authentication authentication) {
        return ApiResponse.success(productService.updateProduct(id, request, employeeId(authentication)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductDetailResponse> updateProductStatus(@PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateProductStatusRequest request,
                                                                  Authentication authentication) {
        return ApiResponse.success(productService.updateProductStatus(id, request.status(), employeeId(authentication)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable UUID id, Authentication authentication) {
        productService.deleteProduct(id, employeeId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/variants")
    public ApiResponse<ProductVariantResponse> createVariant(@PathVariable UUID id, @Valid @RequestBody CreateVariantRequest request,
                                                             Authentication authentication) {
        return ApiResponse.success(productVariantService.createVariant(id, request, employeeId(authentication)));
    }

    @PutMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> updateVariant(@PathVariable UUID id, @PathVariable UUID variantId,
                                                             @Valid @RequestBody UpdateVariantRequest request,
                                                             Authentication authentication) {
        return ApiResponse.success(productVariantService.updateVariant(id, variantId, request, employeeId(authentication)));
    }

    @PatchMapping("/{id}/variants/{variantId}/status")
    public ApiResponse<ProductVariantResponse> updateVariantStatus(@PathVariable UUID id, @PathVariable UUID variantId,
                                                                   @Valid @RequestBody UpdateVariantStatusRequest request,
                                                                   Authentication authentication) {
        return ApiResponse.success(productVariantService.updateVariantStatus(id, variantId, request.status(), employeeId(authentication)));
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public ApiResponse<Void> deleteVariant(@PathVariable UUID id, @PathVariable UUID variantId, Authentication authentication) {
        productVariantService.deleteVariant(id, variantId, employeeId(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/admin")
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getAdminProducts(@Valid @ModelAttribute ProductFilterRequest filter) {
        return ApiResponse.success(productQueryService.getAdminProducts(filter));
    }

    @GetMapping("/admin/{id}")
    public ApiResponse<ProductDetailResponse> getAdminProductById(@PathVariable UUID id) {
        return ApiResponse.success(productQueryService.getAdminProductById(id));
    }

    @GetMapping
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getProducts(@Valid @ModelAttribute ProductFilterRequest filter) {
        return ApiResponse.success(productQueryService.getProducts(filter));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> getProductById(@PathVariable UUID id) {
        return ApiResponse.success(productQueryService.getProductById(id));
    }

    @GetMapping("/slug/{seoName}")
    public ApiResponse<ProductDetailResponse> getProductBySeoName(@PathVariable String seoName) {
        return ApiResponse.success(productQueryService.getProductBySeoName(seoName));
    }

    @GetMapping("/{id}/variants/{variantId}")
    public ApiResponse<ProductVariantResponse> getProductVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        return ApiResponse.success(productQueryService.getProductVariant(id, variantId));
    }

    private UUID employeeId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getClaimAsString(ACCOUNT_ID_CLAIM));
        }
        throw new IllegalStateException("Employee identity is missing from the authentication");
    }
}
