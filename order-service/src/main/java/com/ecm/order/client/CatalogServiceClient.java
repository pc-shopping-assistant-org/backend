package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.ProductVariantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Called during add-to-cart/checkout to confirm a variant is still ACTIVE and read its current price. */
@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    @GetMapping("/product-variants/{id}")
    ProductVariantResponse getVariant(@PathVariable("id") UUID id);

    /** Throwaway call used to verify trace propagation end to end. */
    @GetMapping("/trace-test/ping")
    ApiResponse<String> ping();
}
