package com.ecm.sync.controller;

import com.ecm.common.response.ApiResponse;
import com.ecm.sync.dto.response.DltReplayResponse;
import com.ecm.sync.dto.response.DltStatusResponse;
import com.ecm.sync.dto.response.ProductSyncResponse;
import com.ecm.sync.service.DltReplayService;
import com.ecm.sync.service.ProductSyncService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
public class ProductSyncController {

    private final ProductSyncService syncService;
    private final DltReplayService replayService;

    /** Full rebuild of the index from the catalog (ADMIN). */
    @PostMapping("/sync")
    public ApiResponse<ProductSyncResponse> sync() {
        return ApiResponse.success(syncService.syncAll());
    }

    /** How many catalog changes are parked on the dead-letter topic and not replayed yet (ADMIN). */
    @GetMapping("/sync/dlt")
    public ApiResponse<DltStatusResponse> deadLetterStatus() {
        return ApiResponse.success(replayService.status());
    }

    /** Processes parked catalog changes again, oldest first, up to {@code limit} (ADMIN). */
    @PostMapping("/sync/dlt/replay")
    public ApiResponse<DltReplayResponse> replayDeadLetters(@RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return ApiResponse.success(replayService.replay(limit));
    }
}
