package com.ecm.order.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.order.dto.response.CartVariantDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    /** Price, stock, name, label, category and whether it can be sold, for many variants in one call. */
    @GetMapping("/cart-variant-details")
    ApiResponse<List<CartVariantDetailsResponse>> getCartVariantDetails(@RequestParam("ids") List<UUID> ids);

    @GetMapping("/trace-test/ping")
    ApiResponse<String> ping();
}
