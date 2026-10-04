package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import com.ecm.order.dto.response.ProductDetailResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    @GetMapping("/product-variants/{id}")
    ApiResponse<ProductVariantResponse> getVariant(@PathVariable("id") UUID id);

    @GetMapping("/products/{id}")
    ApiResponse<ProductDetailResponse> getProduct(@PathVariable("id") UUID productId);

    @GetMapping("/cart-variant-details")
    ApiResponse<java.util.List<CartVariantDetailsResponse>> getCartVariantDetails(@org.springframework.web.bind.annotation.RequestParam("ids") java.util.List<UUID> ids);

    @GetMapping("/trace-test/ping")
    ApiResponse<String> ping();
}
