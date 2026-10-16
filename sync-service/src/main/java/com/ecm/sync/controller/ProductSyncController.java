package com.ecm.sync.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.sync.dto.response.ProductSyncResponse;
import com.ecm.sync.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductSyncController {

    private final ProductSyncService syncService;

    /** Full rebuild of the index from the catalog (ADMIN). */
    @PostMapping("/sync")
    public ApiResponse<ProductSyncResponse> sync() {
        return ApiResponse.success(syncService.syncAll());
    }
}
