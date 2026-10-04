package com.ecm.catalog.controller;

import com.ecm.catalog.client.MediaServiceClient;
import com.ecm.catalog.dto.response.BrandResponse;
import com.ecm.catalog.dto.request.CreateBrandRequest;
import com.ecm.catalog.dto.request.UpdateBrandRequest;
import com.ecm.catalog.dto.request.UpdateStatusRequest;
import com.ecm.catalog.dto.response.MediaFileResponse;
import com.ecm.catalog.service.BrandService;
import com.ecm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final MediaServiceClient mediaServiceClient;

    @PostMapping
    public ApiResponse<BrandResponse> create(@jakarta.validation.Valid @RequestBody CreateBrandRequest request) {
        return ApiResponse.success("Brand created", brandService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandResponse> update(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateBrandRequest request) {
        return ApiResponse.success("Brand updated", brandService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<BrandResponse> updateStatus(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.success("Brand status updated", brandService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        brandService.delete(id);
        return ApiResponse.success("Brand deleted", null);
    }

    @GetMapping
    public ApiResponse<List<BrandResponse>> getAllBrands() {
        List<BrandResponse> response = brandService.getAllBrands();
        List<UUID> imageFileIds = response.stream().map(BrandResponse::getImageFileId)
                .filter(id -> id != null).distinct().toList();
        if (!imageFileIds.isEmpty()) {
            var imageUrls = mediaServiceClient.getFiles(imageFileIds).getData().stream()
                    .filter(file -> file.url() != null)
                    .collect(java.util.stream.Collectors.toMap(MediaFileResponse::id, MediaFileResponse::url));
            response.forEach(brand -> brand.setImageUrl(imageUrls.get(brand.getImageFileId())));
        }
        return ApiResponse.success("Get brands successfully", response);
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandResponse> getBrandById(@PathVariable UUID id) {
        BrandResponse response = brandService.getBrandById(id);
        if (response.getImageFileId() != null) {
            response.setImageUrl(mediaServiceClient.getFiles(List.of(response.getImageFileId()))
                    .getData().stream().findFirst().map(MediaFileResponse::url).orElse(null));
        }
        return ApiResponse.success("Get brand successfully", response);
    }
}
