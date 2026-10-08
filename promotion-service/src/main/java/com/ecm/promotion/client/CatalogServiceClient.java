package com.ecm.promotion.client;

import com.ecm.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

/** Used to confirm that the categories a discount targets exist in the Catalog Service and are in use. */
@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {

    /** The categories that are in use (ACTIVE), as a flat list. */
    @GetMapping("/categories")
    ApiResponse<List<CategoryRef>> getActiveCategories();

    record CategoryRef(UUID id) {
    }
}
