package com.ecm.search.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.common.response.PageResponse;
import com.ecm.search.dto.response.ProductSearchResponse;
import com.ecm.search.dto.response.ProductSyncResponse;
import com.ecm.search.service.ProductSearchService;
import com.ecm.search.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductSearchController {

    private final ProductSearchService searchService;
    private final ProductSyncService syncService;

    /** Full rebuild of the index from the catalog (ADMIN). */
    @PostMapping("/sync")
    public ApiResponse<ProductSyncResponse> sync() {
        return ApiResponse.success(syncService.syncAll());
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<ProductSearchResponse>> search(@RequestParam("q") String query,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(searchService.search(query, page, size));
    }
}
