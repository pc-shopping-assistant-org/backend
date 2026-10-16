package com.ecm.sync.client;

import com.ecm.common.response.ApiResponse;
import com.ecm.sync.dto.response.CatalogCategoryResponse;
import com.ecm.sync.dto.response.CatalogProductPage;
import com.ecm.sync.dto.response.CatalogProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * The public product view of the catalog: ACTIVE products with their ACTIVE variants and images, 404 for anything
 * the storefront does not show, which is exactly the set the search should hold.
 */
@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    @GetMapping("/products/{id}")
    ApiResponse<CatalogProductResponse> getProduct(@PathVariable("id") UUID id);

    /** The storefront list, a page at a time; {@code cursor} is the {@code nextCursor} of the previous page. */
    @GetMapping("/products")
    ApiResponse<CatalogProductPage> getProducts(@RequestParam("limit") int limit,
                                                @RequestParam(value = "cursor", required = false) UUID cursor);

    /** Every active category as a flat list, each with its parent. */
    @GetMapping("/categories")
    ApiResponse<List<CatalogCategoryResponse>> getCategories();
}
