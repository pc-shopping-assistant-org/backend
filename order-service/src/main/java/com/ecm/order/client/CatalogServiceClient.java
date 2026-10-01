package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    @GetMapping("/product-variants/{id}")
    ApiResponse<ProductVariantResponse> getVariant(@PathVariable("id") UUID id);

    @GetMapping("/trace-test/ping")
    ApiResponse<String> ping();
}
